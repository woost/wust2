package wust.sdk

import wust.api._, wust.api.serialize.Boopickle._
import covenant.ws._
import chameleon.ext.boopickle._
import boopickle.Default._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import java.nio.ByteBuffer

private[sdk] trait NativeWustClient {
  def apply(location: String)(implicit system: ActorSystem, materializer: ActorMaterializer) = {
    import system.dispatcher
    new WustClientFactory(WsClient[ByteBuffer, ApiEvent, ApiError](location, WustClient.config))
  }
}
