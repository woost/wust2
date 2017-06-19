package wust.frontend

import wust.graph._
import wust.ids._
import rx._
import rxext._

import autowire._
import boopickle.Default._
import scala.concurrent.ExecutionContext
import scala.util.Success
import derive.derive
import wust.util.EventTracker.sendEvent
import scala.collection.mutable

sealed trait SyncStatus
object SyncStatus {
  case object Sending extends SyncStatus
  case object Pending extends SyncStatus
  case object Done extends SyncStatus
  case object Error extends SyncStatus
}

class GraphPersistence(state: GlobalState)(implicit ctx: Ctx.Owner) {
  import Client.storage

  @derive(copyF)
  private case class KnownChanges(cached: GraphChanges, sent: GraphChanges) { val all = sent + cached }

  private val hasError = Var(false)
  private val changes = Var(KnownChanges(storage.graphChanges.getOrElse(GraphChanges.empty), GraphChanges.empty))

  private val history = new mutable.Stack[GraphChanges]
  private val revertedChanges = new mutable.Stack[GraphChanges]

  //TODO better
  val canUndo = Var(history.nonEmpty)
  val canRedo = Var(revertedChanges.nonEmpty)

  val status: Rx[SyncStatus] = Rx {
    if (!changes().sent.isEmpty) SyncStatus.Sending
    else if (hasError()) SyncStatus.Error
    else if (!changes().cached.isEmpty) SyncStatus.Pending
    else SyncStatus.Done
  }

  changes.map(_.all).foreach(storage.graphChanges= _)

  private def enrichChanges(changes: GraphChanges): GraphChanges = {
    import changes.consistent._

    val toDelete = delPosts.flatMap { postId =>
      Collapse.getHiddenPosts(state.displayGraphWithoutParents.now.graph removePosts state.graphSelection.now.parentIds, Set(postId))
    }

    val toOwn = state.selectedGroupId.now.toSet.flatMap { (groupId: GroupId) =>
      addPosts.map(p => Ownership(p.id, groupId))
    }

    val containedPosts = addContainments.map(_.childId)
    val toContain = addPosts
      .filterNot(p => containedPosts(p.id))
      .flatMap(p => GraphSelection.toContainments(state.graphSelection.now, p.id))

    changes.consistent + GraphChanges(delPosts = toDelete, addOwnerships = toOwn, addContainments = toContain)
  }

  def flush()(implicit ec: ExecutionContext): Unit = {
    val current = changes.now
    val newChanges = current.cached
    state.syncMode.now match {
      case _ if newChanges.isEmpty => ()
      case SyncMode.Live =>
        changes() = current.copy(cached = GraphChanges.empty, sent = current.sent + newChanges)
        println(s"persisting changes: $newChanges")
        hasError() = false
        Client.api.changeGraph(newChanges).call().onComplete {
          case Success(true) =>
            changes.updatef(_.copyF(sent = _ - newChanges))
            if (newChanges.addPosts.nonEmpty) sendEvent("graphchanges", "addPosts", "success", newChanges.addPosts.size)
            if (newChanges.addConnections.nonEmpty) sendEvent("graphchanges", "addConnections", "success", newChanges.addConnections.size)
            if (newChanges.addContainments.nonEmpty) sendEvent("graphchanges", "addContainments", "success", newChanges.addContainments.size)
            if (newChanges.updatePosts.nonEmpty) sendEvent("graphchanges", "updatePosts", "success", newChanges.updatePosts.size)
            if (newChanges.delPosts.nonEmpty) sendEvent("graphchanges", "delPosts", "success", newChanges.delPosts.size)
            if (newChanges.delConnections.nonEmpty) sendEvent("graphchanges", "delConnections", "success", newChanges.delConnections.size)
            if (newChanges.delContainments.nonEmpty) sendEvent("graphchanges", "delContainments", "success", newChanges.delContainments.size)
          case _ =>
            changes.updatef(_.copyF(cached = _ + newChanges, sent = _ - newChanges))
            hasError() = true
            sendEvent("graphchanges", "flush", "failure", newChanges.size)
        }
      case _ => println(s"caching changes: $newChanges")
    }
  }

  //TODO: change only the display graph in global state by adding the changes to the rawgraph
  def applyChangesToState(graph: Graph) {
    state.rawGraph() = graph applyChanges changes.now.all
  }

  def addChanges(
    addPosts:        Iterable[Post]        = Set.empty,
    addConnections:  Iterable[Connection]  = Set.empty,
    addContainments: Iterable[Containment] = Set.empty,
    addOwnerships:   Iterable[Ownership]   = Set.empty,
    updatePosts:     Iterable[Post]        = Set.empty,
    delPosts:        Iterable[PostId]      = Set.empty,
    delConnections:  Iterable[Connection]  = Set.empty,
    delContainments: Iterable[Containment] = Set.empty,
    delOwnerships:   Iterable[Ownership]   = Set.empty
  )(implicit ec: ExecutionContext): Unit = {
    val newChanges = GraphChanges.from(addPosts, addConnections, addContainments, addOwnerships, updatePosts, delPosts, delConnections, delContainments, delOwnerships)

    addChanges(newChanges)
  }

  def addChangesEnriched(
    addPosts:        Iterable[Post]        = Set.empty,
    addConnections:  Iterable[Connection]  = Set.empty,
    addContainments: Iterable[Containment] = Set.empty,
    addOwnerships:   Iterable[Ownership]   = Set.empty,
    updatePosts:     Iterable[Post]        = Set.empty,
    delPosts:        Iterable[PostId]      = Set.empty,
    delConnections:  Iterable[Connection]  = Set.empty,
    delContainments: Iterable[Containment] = Set.empty,
    delOwnerships:   Iterable[Ownership]   = Set.empty
  )(implicit ec: ExecutionContext): Unit = {
    val newChanges = enrichChanges(
      GraphChanges.from(addPosts, addConnections, addContainments, addOwnerships, updatePosts, delPosts, delConnections, delContainments, delOwnerships)
    )

    addChanges(newChanges)
  }

  def addChanges(newChanges: GraphChanges)(implicit ec: ExecutionContext): Unit = {
    //TODO fake info about own posts when applying
    state.ownPosts ++= newChanges.addPosts.map(_.id)
    //TODO fake info about post creation
    val currentTime = System.currentTimeMillis
    state.postTimes ++= newChanges.addPosts.map(_.id -> currentTime)

    revertedChanges.clear()
    history.push(newChanges)
    canUndo() = history.nonEmpty
    canRedo() = revertedChanges.nonEmpty

    changes.updatef(_.copyF(cached = _ + newChanges.consistent))
    applyChangesToState(state.rawGraph.now)
    flush()
  }

  def undoChanges()(implicit ec: ExecutionContext): Unit = {
    if (history.nonEmpty) {
      val changesToRevert = history.pop()
      revertedChanges.push(changesToRevert)
      val cachedChanges = changes.now.cached
      val cachedChangesAfter = cachedChanges - changesToRevert
      if (cachedChangesAfter.size == cachedChanges.size) {
        changes.updatef(_.copyF(cached = _ + changesToRevert.revert))
        applyChangesToState(state.rawGraph.now)
      } else {
        changes.now.copy(cached = cachedChangesAfter)
        state.rawGraph() = state.rawGraph.now applyChanges (changes.now.all + changesToRevert.revert)
      }

      canUndo() = history.nonEmpty
      canRedo() = revertedChanges.nonEmpty

      flush()
    }
  }

  def redoChanges()(implicit ec: ExecutionContext): Unit = {
    if (revertedChanges.nonEmpty) {
      val changesToRedo = revertedChanges.pop()
      history.push(changesToRedo)
      val cachedChanges = changes.now.cached
      val cachedChangesAfter = cachedChanges - changesToRedo.revert
      if (cachedChangesAfter.size == cachedChanges.size) {
        //TODO need to add undelete instead of addposts
        val changesWithUndelete = changesToRedo.copy(addPosts = Set.empty, undeletePosts = changesToRedo.addPosts.map(_.id))
        changes.updatef(_.copyF(cached = _ + changesWithUndelete))
      } else {
        changes.now.copy(cached = cachedChangesAfter)
      }

      state.rawGraph() = state.rawGraph.now applyChanges (changes.now.all + changesToRedo)

      canUndo() = history.nonEmpty
      canRedo() = revertedChanges.nonEmpty

      flush()
    }
  }
}
