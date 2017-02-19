package frontend

import graph._, api._
import mhtml._

case class InteractionMode[E,F](edit: Option[E], focus: Option[F])
object FocusMode {
  def unapply[F,E](mode: InteractionMode[E,F]): Option[F] = Some(mode) collect {
    case InteractionMode(None, Some(f)) => f
  }
}
object EditMode {
  def unapply[F,E](mode: InteractionMode[E,F]): Option[E] = Some(mode) collect {
    case InteractionMode(Some(e), _) => e
  }
}

class GlobalState {
  val graph = VarRx(Graph.empty)
    .map(_.consistent)

  val focusedPostId = VarRx[Option[AtomId]](None)
    .flatMap(source => graph.map(g => source.filter(g.posts.isDefinedAt)))

  val editedPostId = VarRx[Option[AtomId]](None)
    .flatMap(source => graph.map(g => source.filter(g.posts.isDefinedAt)))

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
