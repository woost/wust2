package frontend

import boopickle.Default._

import diode._, Action._
import framework._
import api._, graph._

object TypePicklers {
  implicit val channelPickler = implicitly[Pickler[Channel]]
  implicit val eventPickler = implicitly[Pickler[ApiEvent]]
  implicit val authPickler = implicitly[Pickler[Authorize]]
  implicit val errorPickler = implicitly[Pickler[ApiError]]
}
import TypePicklers._

object Action {
    implicit object actionType extends ActionType[ApiEvent]
}
import Action._

case class BadRequestException(error: ApiError) extends Exception

object Client extends WebsocketClient[Channel, ApiEvent, ApiError, Authorize] {
  val wireApi = wire[Api]

  def fromError(error: ApiError) = BadRequestException(error)
  def receive(event: ApiEvent) = AppCircuit.dispatch(event)
}
