package frontend

import graph._, api._
import mhtml._

class GlobalState {
  val graph = Var(Graph.empty)
  //TODO focusedPost indirection for consistency
  val focusedPost: Var[Option[AtomId]] = Var(None)

  val onApiEvent: ApiEvent => Unit = _ match {
    case NewPost(post) => graph.update(_ + post)
    case NewConnection(connects) => graph.update(_ + connects)
    case NewContainment(contains) => graph.update(_ + contains)

    case DeletePost(postId) => graph.update(_.removePost(postId))
    case DeleteConnection(connectsId) => graph.update(_.removeConnection(connectsId))
    case DeleteContainment(containsId) => graph.update(_.removeContainment(containsId))
  }
}
