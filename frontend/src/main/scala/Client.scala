package frontend

import boopickle.Default._

import diode.Action, Action._
import framework._
import api._, graph._

case class SetCounter(value: Int) extends Action
case class SetGraph(graph: Graph) extends Action
case class AddPost(post: Post) extends Action
case class AddRespondsTo(respondsTo: RespondsTo) extends Action

object TypePicklers {
  implicit val channelPickler = implicitly[Pickler[Channel]]
  implicit val eventPickler = implicitly[Pickler[ApiEvent]]
  implicit val authPickler = implicitly[Pickler[Authorize]]
}
import TypePicklers._

object Client extends WebsocketClient[Channel, ApiEvent, Authorize] {
  val wireApi = wire[Api]

  val map: PartialFunction[ApiEvent, Action] = {
    case NewCounterValue(_, value) => SetCounter(value)
    case NewPost(atom) => AddPost(atom)
    case NewRespondsTo(atom) => AddRespondsTo(atom)
  }

  val dispatch: ApiEvent => Unit = map.andThen(a => AppCircuit.dispatch(a)) orElse { case e => println(s"unknown event: $e") }
  def receive(event: ApiEvent) = dispatch(event)

  subscribe(Channel.Counter)
  subscribe(Channel.Graph)
}
