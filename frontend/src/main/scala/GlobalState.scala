package frontend

import graph._, api._
import rx._
import RxVar._

sealed trait InteractionMode
case class FocusMode(postId: AtomId) extends InteractionMode
case class EditMode(postId: AtomId) extends InteractionMode
case object DefaultMode extends InteractionMode

class GlobalState(implicit ctx: Ctx.Owner) {
  val rawGraph = RxVar(Graph.empty)
    .map(_.consistent)

  val collapsedPostIds = RxVar[Set[AtomId]](Set.empty)

  val currentView = {
    val v = RxVar[View](View())
    RxVar(v.write, Rx {
      v().union(View(collapsed = Selector.IdSet(collapsedPostIds())))
    })
  }

  val graph = {
    RxVar(rawGraph.write, Rx {
      View(currentView(), rawGraph())
    })
  }

  val focusedPostId = RxVar[Option[AtomId]](None)
    .flatMap(source => graph.map(g => source.filter(g.posts.isDefinedAt)))

  val editedPostId = RxVar[Option[AtomId]](None)
    .flatMap(source => graph.map(g => source.filter(g.posts.isDefinedAt)))

  val mode: Rx[InteractionMode] = Rx {
    (focusedPostId(), editedPostId()) match {
      case (_, Some(id)) => EditMode(id)
      case (Some(id), None) => FocusMode(id)
      case _ => DefaultMode
    }
  }

  rawGraph.rx.debug(v => s"rawGraph: ${v.posts.size} posts, ${v.connections.size} connections, ${v.containments.size} containments")
  collapsedPostIds.rx.debug("collapsedPostIds")
  currentView.rx.debug("currentView")
  graph.rx.debug(v => s"graph: ${v.posts.size} posts, ${v.connections.size} connections, ${v.containments.size} containments")
  focusedPostId.rx.debug("focusedPostId")
  editedPostId.rx.debug("editedPostId")
  mode.rx.debug("mode")

  val onApiEvent: ApiEvent => Unit = _ match {
    case NewPost(post) => graph.update(_ + post)
    case UpdatedPost(post) => graph.update(_ + post)
    case NewConnection(connects) => graph.update(_ + connects)
    case NewContainment(contains) => graph.update(_ + contains)

    case DeletePost(postId) => graph.update(_.removePost(postId))
    case DeleteConnection(connectsId) => graph.update(_.removeConnection(connectsId))
    case DeleteContainment(containsId) => graph.update(_.removeContainment(containsId))
  }
}
