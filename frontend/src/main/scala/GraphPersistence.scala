package wust.frontend

import wust.graph._
import wust.ids._
import rx._
import rxext._

import autowire._
import boopickle.Default._
import scala.concurrent.ExecutionContext
import scala.util.Success

sealed trait SyncStatus
object SyncStatus {
  case object Sending extends SyncStatus
  case object Pending extends SyncStatus
  case object Done extends SyncStatus
  case object Error extends SyncStatus
}

sealed trait SyncMode
object SyncMode {
  case object Live extends SyncMode
  case object Offline extends SyncMode

  val fromString: PartialFunction[String, SyncMode] = {
    case "Live" => Live
    case "Offline" => Offline
  }

  val default = Live
  val all = Seq(Live, Offline)
}

class GraphPersistence(state: GlobalState)(implicit ctx: Ctx.Owner) {
  import Client.storage

  private val isSending = Var[Int](0)
  private val hasError = Var[Boolean](false)
  private val currChanges = Var[GraphChanges](storage.graphChanges.getOrElse(GraphChanges.empty))

  val mode = Var[SyncMode](storage.syncMode.getOrElse(SyncMode.default))
  val changes: Rx[GraphChanges] = currChanges
  val status: Rx[SyncStatus] = Rx {
    if (isSending() > 0) SyncStatus.Sending
    else if (hasError()) SyncStatus.Error
    else if (changes().isEmpty) SyncStatus.Done
    else SyncStatus.Pending
  }

  //TODO: why does triggerlater not work?
  mode.foreach(storage.syncMode = _)
  changes.foreach(storage.graphChanges = _)

  //TODO: where?
  // should this also add selection containments?
  private def enrichChanges(changes: GraphChanges): GraphChanges = {
    import changes.consistent._

    val toDelete = delPosts.flatMap { postId =>
      Collapse.getHiddenPosts(state.displayGraph.now.graph removePosts state.graphSelection.now.parentIds, Set(postId))
    }

    val toOwn = state.selectedGroupId.now.toSet.flatMap { (groupId: GroupId) =>
      addPosts.map(p => Ownership(p.id, groupId))
    }

    changes.consistent + GraphChanges(delPosts = toDelete, addOwnerships = toOwn)
  }

  def flush()(implicit ec: ExecutionContext): Unit = mode.now match {
    case SyncMode.Live =>
      val changes = currChanges.now
      if (!changes.isEmpty) {
        println(s"persisting changes: $changes")
        isSending.updatef(_ + 1)
        hasError() = false
        Client.api.changeGraph(changes).call().onComplete {
          case Success(true) =>
            isSending.updatef(_ - 1)
            currChanges.updatef(changes - _)
          case _ =>
            isSending.updatef(_ - 1)
            hasError() = true
            //TODO handle
        }
      }
    case _ => println(s"caching changes: $changes")
  }

  //TODO: change only the display graph in global state by adding the changes to the rawgraph
  def applyChangesToState(graph: Graph) {
    val newGraph = graph applyChanges currChanges.now
    state.rawGraph() = newGraph
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

    val changes = enrichChanges(
      GraphChanges.from(addPosts, addConnections, addContainments, addOwnerships, updatePosts, delPosts, delConnections, delContainments, delOwnerships)
    )

    currChanges.updatef(_ + changes)
    applyChangesToState(state.rawGraph.now)

    flush()
  }
}

