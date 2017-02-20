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
  val graph = RxVar(Graph.empty)
    .map(_.consistent)

  val focusedPostId = RxVar[Option[AtomId]](None)
    .flatMap(source => graph.map(g => source.filter(g.posts.isDefinedAt)))

  val editedPostId = RxVar[Option[AtomId]](None)
    .flatMap(source => graph.map(g => source.filter(g.posts.isDefinedAt)))

  //TODO: better?
  val collapsedPosts = new collection.mutable.HashMap[AtomId, Var[Boolean]] {
    def safeGet(key: AtomId) = getOrElseUpdate(key, Var(false))
  }
  graph.foreach { graph =>
    collapsedPosts --= collapsedPosts.keys.filterNot(graph.posts.keys.toSet)
  }


  val mode = editedPostId.flatMap(e => focusedPostId.map(f => InteractionMode(edit = e, focus = f)))

  // graph.foreach(v => println(s"graph update: $v"))
  // focusedPostId.foreach(v => println(s"focusedPostId update: $v"))

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
