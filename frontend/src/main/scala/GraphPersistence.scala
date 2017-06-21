package wust.frontend

import wust.graph._
import wust.ids._
import wust.util.collection._
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

  private val hasError = Var(false)
  private val isSending = Var(0)
  private val localChanges = Var(storage.localGraphChanges)

  private val undoHistory = new mutable.Stack[GraphChanges]
  private val redoHistory = new mutable.Stack[GraphChanges]
  private val deletedPostsById = new mutable.HashMap[PostId, Post]

  //TODO better
  val canUndo = Var(undoHistory.nonEmpty)
  val canRedo = Var(redoHistory.nonEmpty)

  val status: Rx[SyncStatus] = Rx {
    if (isSending() > 0) SyncStatus.Sending
    else if (hasError()) SyncStatus.Error
    else if (!localChanges().isEmpty) SyncStatus.Pending
    else SyncStatus.Done
  }

  localChanges.foreach(storage.localGraphChanges = _)

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

    changes.consistent merge GraphChanges(delPosts = toDelete, addOwnerships = toOwn, addContainments = toContain)
  }

  def flush()(implicit ec: ExecutionContext): Unit = {
    val newChanges = localChanges.now
    state.syncMode.now match {
      case _ if newChanges.isEmpty => ()
      case SyncMode.Live =>
        localChanges() = List.empty
        hasError() = false
        isSending.updatef(_ + 1)
        println(s"persisting localChanges: $newChanges")
        Client.api.changeGraph(newChanges).call().onComplete {
          case Success(true) =>
            isSending.updatef(_ - 1)

            val compactChanges = newChanges.foldLeft(GraphChanges.empty)(_ merge _)
            if (compactChanges.addPosts.nonEmpty) sendEvent("graphchanges", "addPosts", "success", compactChanges.addPosts.size)
            if (compactChanges.addConnections.nonEmpty) sendEvent("graphchanges", "addConnections", "success", compactChanges.addConnections.size)
            if (compactChanges.addContainments.nonEmpty) sendEvent("graphchanges", "addContainments", "success", compactChanges.addContainments.size)
            if (compactChanges.updatePosts.nonEmpty) sendEvent("graphchanges", "updatePosts", "success", compactChanges.updatePosts.size)
            if (compactChanges.delPosts.nonEmpty) sendEvent("graphchanges", "delPosts", "success", compactChanges.delPosts.size)
            if (compactChanges.delConnections.nonEmpty) sendEvent("graphchanges", "delConnections", "success", compactChanges.delConnections.size)
            if (compactChanges.delContainments.nonEmpty) sendEvent("graphchanges", "delContainments", "success", compactChanges.delContainments.size)
          case _ =>
            localChanges.updatef(newChanges ++ _)
            isSending.updatef(_ - 1)
            hasError() = true
            println(s"failed to persist localChanges: $newChanges")
            sendEvent("graphchanges", "flush", "failure", newChanges.size)
        }
      case _ => println(s"caching localChanges: $newChanges")
    }
  }

  //TODO: change only the display graph in global state by adding the localChanges to the rawgraph
  def applyChangesToState(graph: Graph) {
    val compactChanges = localChanges.now.foldLeft(GraphChanges.empty)(_ merge _)
    state.rawGraph() = graph applyChanges compactChanges
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

  def addChanges(newChanges: GraphChanges)(implicit ec: ExecutionContext): Unit = if (newChanges.nonEmpty) {
    //TODO fake info about own posts when applying
    state.ownPosts ++= newChanges.addPosts.map(_.id)
    //TODO fake info about post creation
    val currentTime = System.currentTimeMillis
    state.postTimes ++= newChanges.addPosts.map(_.id -> currentTime)

    // we need store all deleted posts to be able to reconstruct them when
    // undoing post deletion (need to add them again)
    deletedPostsById ++= newChanges.delPosts.map(id => state.rawGraph.now.postsById(id)).by(_.id)

    redoHistory.clear()
    undoHistory.push(newChanges)

    saveAndApplyChanges(newChanges.consistent)
  }

  def undoChanges()(implicit ec: ExecutionContext): Unit = if (undoHistory.nonEmpty) {
    val changesToRevert = undoHistory.pop()
    redoHistory.push(changesToRevert)
    saveAndApplyChanges(changesToRevert.revert(deletedPostsById))
  }

  def redoChanges()(implicit ec: ExecutionContext): Unit = if (redoHistory.nonEmpty) {
    val changesToRedo = redoHistory.pop()
    undoHistory.push(changesToRedo)
    saveAndApplyChanges(changesToRedo)
  }

  private def saveAndApplyChanges(changes: GraphChanges)(implicit ec: ExecutionContext) {
    canUndo() = undoHistory.nonEmpty
    canRedo() = redoHistory.nonEmpty

    localChanges.updatef(_ :+ changes)
    applyChangesToState(state.rawGraph.now)
    flush()
  }
}
