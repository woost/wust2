package wust.sdk

import wust.api.ApiEvent
import mycelium.client._

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import java.nio.ByteBuffer

object AkkaWustClient {
  def apply(location: String, handler: IncidentHandler[ApiEvent])(implicit system: ActorSystem, materializer: ActorMaterializer) = {
    import system.dispatcher
    val config = AkkaWebsocketConfig(bufferSize = 500, overflowStrategy = OverflowStrategy.fail)
    val connection = new AkkaWebsocketConnection[ByteBuffer](config)
    WustClientFactory.createAndRun(location, handler, connection)
  }
}
