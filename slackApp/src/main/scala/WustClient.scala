package wust.slack

import akka.actor.ActorSystem
import autowire._
import boopickle.Default._
import wust.api._
import wust.ids._
import wust.framework._
import scala.concurrent.ExecutionContext.Implicits.global

trait ApiIncidentHandler extends IncidentHandler[ApiEvent, ApiError] {
  override def fromError(error: ApiError): Throwable = ApiException(error)
}

class WustClient(ws: WebsocketClient[ApiEvent, ApiError]) {
  val api = ws.wire[Api]
  val auth = ws.wire[AuthApi]
  def stop() = ws.stop()
}

object WustClient {
  def apply(location: String, handler: IncidentHandler[ApiEvent, ApiError])(implicit system: ActorSystem) = {
    val connection = new ReconnectingWebsocketConnection(new AkkaWebsocketConnection())
    val ws = new WebsocketClient[ApiEvent, ApiError](connection)
    ws.run(location, handler)
    new WustClient(ws)
  }
}
