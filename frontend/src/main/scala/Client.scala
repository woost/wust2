package frontend

import boopickle.Default._

import diode.Action, Action._
import framework._
import api._, graph._
import pharg._

case class SetCounter(value: Int) extends Action
case class AddPost(id: Id, post: Post) extends Action
case class AddConnects(edge: Edge[Id], connects: Connects) extends Action

object TypePicklers {
  implicit val channelPickler = implicitly[Pickler[Channel]]
  implicit val eventPickler = implicitly[Pickler[ApiEvent]]
}
import TypePicklers._

object Client extends WebsocketClient[Channel, ApiEvent] {
  val channelPickler = implicitly[Pickler[Channel]]
  val eventPickler = implicitly[Pickler[ApiEvent]]
  val wireApi = wire[Api]

  val map: PartialFunction[ApiEvent, Action] = {
    case NewCounterValue(value) => SetCounter(value)
    case NewPost(id, value) => AddPost(id, value)
    case NewConnects(edge, value) => AddConnects(edge, value)
  }

  val dispatch: ApiEvent => Unit = map.andThen(a => AppCircuit.dispatch(a)) orElse { case e => println(s"unknown event: $e") }
  def receive(event: ApiEvent) = dispatch(event)

  subscribe(Channel.Counter)
  subscribe(Channel.Graph)
}
