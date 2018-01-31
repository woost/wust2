package wust.sdk

import wust.api.ApiEvent
import mycelium.client._

import java.nio.ByteBuffer
import scala.concurrent.ExecutionContext

object JsWustClient {
  def apply(location: String, handler: IncidentHandler[ApiEvent])(implicit ec: ExecutionContext) = {
    val connection = new JsWebsocketConnection[ByteBuffer]
    WustClientFactory.createAndRun(location, handler, connection)
  }
}
