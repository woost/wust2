package frontend

import graph._, api._
import mhtml._

class SourceVar[S, A](source: Var[S], mapping: Rx[S] => Rx[A]) { //TODO: extends Rx[A],  but Rx is sealed
  val target = mapping(source)

  def :=(newValue: S) = source := newValue
  def update(f: S => S) = source.update(f)

  def map[B](f: A => B): SourceVar[S, B] = new SourceVar(source, (_: Rx[S]) => target.map(f))
  def value = target.value
  def foreach(s: A => Unit) = target.foreach(s)
  def flatMap[B](s: A => Rx[B]): SourceVar[S, B] = new SourceVar(source, (_: Rx[S]) => target.flatMap(s))
}

class GlobalState {
  val graph = Var(Graph.empty)
  val focusedPostId = new SourceVar(
    source = Var[Option[AtomId]](None),
    (source: Rx[Option[AtomId]]) => source.flatMap(source => graph.map(g => source.filter(g.posts.isDefinedAt)))
  )

  // graph.foreach(v => println(s"graph update: $v"))
  // focusedPostId.foreach(v => println(s"focusedPostId update: $v"))

  val onApiEvent: ApiEvent => Unit = _ match {
    case NewPost(post) => graph.update(_ + post)
    case NewConnection(connects) => graph.update(_ + connects)
    case NewContainment(contains) => graph.update(_ + contains)

    case DeletePost(postId) => graph.update(_.removePost(postId))
    case DeleteConnection(connectsId) => graph.update(_.removeConnection(connectsId))
    case DeleteContainment(containsId) => graph.update(_.removeContainment(containsId))
  }
}
