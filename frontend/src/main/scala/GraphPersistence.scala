package wust.frontend

import wust.graph._
import wust.ids._
import rx._
import rxext._

import autowire._
import boopickle.Default._
import scala.concurrent.ExecutionContext

case class SyncStatus(isSending: Boolean, hasUnsyncedChanges: Boolean)
trait SyncMode
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

object GraphPersistence {
}
class GraphPersistence(state: GlobalState)(implicit ctx: Ctx.Owner) {
  import GraphPersistence._

  private val isSending = Var[Int](0)
  private val current = Var[GraphChanges](GraphChanges.empty)
  val syncStatus: Rx[SyncStatus] = Rx {
    SyncStatus(isSending() > 0, !current().isEmpty)
  }

  val mode = Var[SyncMode](SyncMode.default)

  {
    import scala.concurrent.ExecutionContext.Implicits.global //TODO
    mode.foreach { (mode: SyncMode) =>
      if (mode == SyncMode.Live) flush()
    }
  }

  def flush()(implicit ec: ExecutionContext) {
    val changes = current.now
    if (!changes.isEmpty) {
      current() = GraphChanges.empty
      isSending.updatef(_ + 1)
      Client.api.changeGraph(changes).call().map { success =>
        isSending.updatef(_ - 1)
        if (success) println(s"persisted graph changes: $changes")
        else {
          println(s"ERROR while persisting graph changes: $changes")
          // current() = changes + current.now
        }
      }
    }
  }

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

    changes + GraphChanges(delPosts = toDelete, addOwnerships = toOwn)
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

    current.updatef(_ + changes)

    mode.now match {
      case SyncMode.Live    => flush()
      case SyncMode.Offline => println(s"caching changes: $changes")
    }

    val newGraph = state.rawGraph.now applyChanges changes
    state.rawGraph() = newGraph
  }
}

