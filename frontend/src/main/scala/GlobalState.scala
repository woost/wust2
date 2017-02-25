package frontend

import graph._, api._
import mhtml._

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

class GlobalState {
  val rawGraph = RxVar(Graph.empty)
    .map(_.consistent)

  val currentView = RxVar[View](View.All())

  val viewedGraph = rawGraph.flatMap(g => currentView.map(View.apply(_, g)))

  val collapsedPostIds = RxVar[Set[AtomId]](Set.empty)
    .flatMap(source => rawGraph.map(g => source.filter(g.posts.isDefinedAt)))

  val focusedPostId = RxVar[Option[AtomId]](None)
    .flatMap(source => rawGraph.map(g => source.filter(g.posts.isDefinedAt)))

  val graph = viewedGraph.flatMap(g => collapsedPostIds.map{ collapsed => View.apply(View.Collapse(collapsed), g) })

  val editedPostId = RxVar[Option[AtomId]](None)
    .flatMap(source => rawGraph.map(g => source.filter(g.posts.isDefinedAt)))

  val mode = editedPostId.flatMap(e => focusedPostId.map(f => InteractionMode(edit = e, focus = f)))

  // collapsedPostIds.debug("collapsed")
  // rawGraph.debug("rawGraph")
  graph.debug("graph")

  val onApiEvent: ApiEvent => Unit = _ match {
    case NewPost(post)                 => graph.update(_ + post)
    case UpdatedPost(post)             => graph.update(_ + post)
    case NewConnection(connects)       => graph.update(_ + connects)
    case NewContainment(contains)      => graph.update(_ + contains)

    case DeletePost(postId)            => graph.update(_.removePost(postId))
    case DeleteConnection(connectsId)  => graph.update(_.removeConnection(connectsId))
    case DeleteContainment(containsId) => graph.update(_.removeContainment(containsId))
  }
}
