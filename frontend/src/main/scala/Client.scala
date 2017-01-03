package frontend

import boopickle.Default._

import diode.Action, Action._
import framework._
import api._, graph._

object TypePicklers {
  implicit val channelPickler = implicitly[Pickler[Channel]]
  implicit val eventPickler = implicitly[Pickler[ApiEvent]]
  implicit val authPickler = implicitly[Pickler[Authorize]]
}
import TypePicklers._

object Client extends WebsocketClient[Channel, ApiEvent, Authorize] {
  val wireApi = wire[Api]

  val map: PartialFunction[ApiEvent, Action] = {
    case NewPost(atom) => AddPost(atom)
    case DeletePost(atomId) => RemovePost(atomId)
    case NewRespondsTo(atom) => AddRespondsTo(atom)
  }

  val dispatch: ApiEvent => Unit = map.andThen(a => AppCircuit.dispatch(a)) orElse { case e => println(s"unknown event: $e") }
  def receive(event: ApiEvent) = dispatch(event)

  subscribe(Channel.Graph)
}
