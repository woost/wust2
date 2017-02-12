package frontend

import collection.mutable
import boopickle.Default._

import framework._
import api._, graph._

object TypePicklers {
  implicit val channelPickler = implicitly[Pickler[Channel]]
  implicit val eventPickler = implicitly[Pickler[ApiEvent]]
  implicit val authPickler = implicitly[Pickler[Authorize]]
  implicit val errorPickler = implicitly[Pickler[ApiError]]
}
import TypePicklers._

case class BadRequestException(error: ApiError) extends Exception

object Client extends WebsocketClient[Channel, ApiEvent, ApiError, Authorize] {
  val api = wire[Api]
  val auth = wire[AuthApi]

  private val listeners = mutable.ArrayBuffer.empty[ApiEvent => Unit]

  def listen(handler: ApiEvent => Unit) = {
    listeners += handler
  }

  override def fromError(error: ApiError) = BadRequestException(error)
  override def receive(event: ApiEvent) = listeners.foreach(_(event))
}
