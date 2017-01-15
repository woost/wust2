package frontend

import boopickle.Default._

import diode._, Action._
import framework._
import api._, graph._

object TypePicklers {
  implicit val channelPickler = implicitly[Pickler[Channel]]
  implicit val eventPickler = implicitly[Pickler[ApiEvent]]
  implicit val authPickler = implicitly[Pickler[Authorize]]
}
import TypePicklers._

object Action {
    implicit object actionType extends ActionType[ApiEvent]
}
import Action._

object Client extends WebsocketClient[Channel, ApiEvent, Authorize] {
  val wireApi = wire[Api]

  def receive(event: ApiEvent) = AppCircuit.dispatch(event)
}
