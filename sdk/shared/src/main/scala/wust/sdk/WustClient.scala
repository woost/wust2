package wust.sdk

import java.nio.ByteBuffer

import boopickle.Default._
import chameleon.ext.boopickle._
import covenant.ws._
import mycelium.client._
import sloth._
import wust.api._
import wust.api.serialize.Boopickle._

import scala.concurrent.duration._

class WustClient[F[_]](client: Client[ByteBuffer, F, ClientException]) {
  val api = client.wire[Api[F]]
  val auth = client.wire[AuthApi[F]]
  val push = client.wire[PushApi[F]]
}
object WustClient extends NativeWustClient {
  val config = WebsocketClientConfig(pingInterval = 50 seconds) // needs to be in sync with idle timeout of backend
}

class WustClientFactory[F[_]](
    val client: WsClient[ByteBuffer, F, ApiEvent, ApiError, ClientException]
) {
  def sendWith(
      sendType: SendType = SendType.WhenConnected,
      requestTimeout: FiniteDuration = 60 seconds
  ): WustClient[F] = new WustClient[F](client.sendWith(sendType, requestTimeout))
  def observable = client.observable
  lazy val nowOrFail = sendWith(SendType.NowOrFail)
  lazy val highPriority = sendWith(SendType.WhenConnected.highPriority)
  lazy val lowPriority = sendWith(SendType.WhenConnected.lowPriority)
  lazy val defaultPriority = sendWith(SendType.WhenConnected)
}
