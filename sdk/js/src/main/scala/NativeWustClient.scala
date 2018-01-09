package wust.sdk

import wust.api.ApiEvent
import mycelium.client._

import java.nio.ByteBuffer
import scala.concurrent.ExecutionContext

trait NativeWustClient {
  def apply(location: String, handler: IncidentHandler[ApiEvent])(implicit ec: ExecutionContext) = {
    val config = JsWebsocketConfig()
    val connection = JsWebsocketConnection[ByteBuffer](config)
    WustClientFactory(location, handler, connection)
  }
}
