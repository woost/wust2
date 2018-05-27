package wust.sdk

import wust.api._, wust.api.serialize.Boopickle._
import boopickle.Default._
import covenant.ws._
import sloth._
import mycelium.client._
import chameleon.ext.boopickle._
import cats.implicits._

import java.nio.ByteBuffer
import scala.concurrent.Future
import scala.concurrent.duration._

class WustClient(client: Client[ByteBuffer, Future, ClientException]) {
  val api = client.wire[Api[Future]]
  val auth = client.wire[AuthApi[Future]]
  val push = client.wire[PushApi[Future]]
}
object WustClient extends NativeWustClient {
  val config = WebsocketClientConfig(pingInterval = 100 seconds) // needs to be in sync with idle timeout of backend
}

class WustClientFactory(val client: WsClient[ByteBuffer, Future, ApiEvent, ApiError, ClientException]) {
  def sendWith(sendType: SendType = SendType.WhenConnected, requestTimeout: FiniteDuration = 30 seconds): WustClient = new WustClient(client.sendWith(sendType, requestTimeout))
  def observable = client.observable
  lazy val nowOrFail = sendWith(SendType.NowOrFail)
  lazy val highPriority = sendWith(SendType.WhenConnected.highPriority)
  lazy val lowPriority = sendWith(SendType.WhenConnected.lowPriority)
  lazy val defaultPriority = sendWith(SendType.WhenConnected)
}
