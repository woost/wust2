package frontend

import graph._, api._
import mhtml._

class GlobalState {
  val graph = new SourceVar(
    source = Var(Graph.empty),
    (source: Rx[Graph]) => source.map(_.consistent)
  )

  val focusedPostId = new SourceVar(
    source = Var[Option[AtomId]](None),
    (source: Rx[Option[AtomId]]) => source.flatMap(source => graph.map(g => source.filter(g.posts.isDefinedAt)))
  )

  val editedPostId = new SourceVar(
    source = Var[Option[AtomId]](None),
    (source: Rx[Option[AtomId]]) => source.flatMap(source => graph.map(g => source.filter(g.posts.isDefinedAt)))
  )

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
