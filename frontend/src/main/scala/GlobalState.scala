package frontend

import graph._, api._
import rx._
import RxVar._

case class InteractionMode(edit: Option[AtomId], focus: Option[AtomId])
object FocusMode {
  def unapply(mode: InteractionMode): Option[AtomId] = Some(mode) collect {
    case InteractionMode(None, Some(f)) => f
  }
}
object EditMode {
  def unapply(mode: InteractionMode): Option[AtomId] = Some(mode) collect {
    case InteractionMode(Some(e), _) => e
  }
}

class GlobalState(implicit ctx: Ctx.Owner) {
  val rawGraph = RxVar(Graph.empty)
    .map(_.consistent)

  val collapsedPostIds = RxVar[Set[AtomId]](Set.empty)

  val currentView = RxVar[View](View())
    .flatMap(v => collapsedPostIds.map(collapsed => v.union(View(collapsed = Selector.IdSet(collapsed)))))

  val graph = rawGraph.flatMap(g => currentView.map(View(_, g)))

  val focusedPostId = RxVar[Option[AtomId]](None)
    .flatMap(source => graph.map(g => source.filter(g.posts.isDefinedAt)))

  val editedPostId = RxVar[Option[AtomId]](None)
    .flatMap(source => graph.map(g => source.filter(g.posts.isDefinedAt)))

  val mode: Rx[InteractionMode] = editedPostId
    .flatMap(e => focusedPostId.map(f => InteractionMode(edit = e, focus = f)))

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
