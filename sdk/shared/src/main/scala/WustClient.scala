package wust.sdk

import wust.api._, serialize.Boopickle._
import wust.ids._

import boopickle.Default._
import sloth.core._
import sloth.mycelium._
import sloth.client._
import mycelium.client._
import chameleon.boopickle._
import cats.implicits._
import shapeless._

import java.nio.ByteBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

case class ApiException(error: ApiError) extends Exception(s"Api returned error: $error")
object ApiException {
  implicit def clientFailureConvert = new ClientFailureConvert[ApiException] {
    def convert(failure: ClientFailure): ApiException = ApiException(ApiError.ClientError(failure.toString))
  }
}

class WustClient(client: Client[ByteBuffer, Future, ApiException]) {
  val api = client.wire[Api[Future]]
  val auth = client.wire[AuthApi[Future]]
}

class WustClientFactory private(ws: WebsocketClient[ByteBuffer, ApiEvent, ApiError])(implicit ec: ExecutionContext) {
  def sendWith(sendType: SendType, requestTimeout: FiniteDuration): WustClient = {
    val client = Client[ByteBuffer, Future, ApiException](ws.toTransport(sendType, requestTimeout, onError = err => new ApiException(err)))
    new WustClient(client)
  }
}
object WustClientFactory {
  def createAndRun(location: String, handler: IncidentHandler[ApiEvent], connection: WebsocketConnection[ByteBuffer])(implicit ec: ExecutionContext): WustClientFactory = {
    val config = WebsocketClientConfig(pingInterval = 100 seconds) // needs to be in sync with idle timeout of backend
    val ws = WebsocketClient[ByteBuffer, ApiEvent, ApiError](connection, config, handler)
    ws.run(location)

    new WustClientFactory(ws)
  }
}
