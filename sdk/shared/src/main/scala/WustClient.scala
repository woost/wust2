package wust.sdk

import wust.api._
import wust.graph.GraphChanges
import wust.ids._

import boopickle.Default._
import sloth.core._
import sloth.boopickle._
import sloth.mycelium._
import sloth.client._
import mycelium.client._
import cats.implicits._
import shapeless._

import java.nio.ByteBuffer
import scala.concurrent.{ExecutionContext, Future}

//TODO: why do we need this? as we can see, these picklers can be resolved implicitly, but somehow we need to use them explicitly.
object HelpMePickle {
  val graphChanges = implicitly[Pickler[List[GraphChanges]]]
  val apiEvents = implicitly[Pickler[List[ApiEvent]]]
}

class WustClient(ws: Client[ByteBuffer, Future, SlothClientFailure.SlothException]) {
  private implicit val iShouldNotBeHere = HelpMePickle.graphChanges
  val api = ws.wire[Api[Future]]
  val auth = ws.wire[AuthApi[Future]]
}
object WustClient extends NativeWustClient

case class ApiException(error: ApiError) extends Exception(s"Api returned error: $error")

private[sdk] object WustClientFactory {
  private implicit val iShouldNotBeHere = HelpMePickle.apiEvents
  private implicit def ApiErrorIsThrowable(error: ApiError): Throwable = ApiException(error)

  def apply(location: String, handler: IncidentHandler[ApiEvent], connection: WebsocketConnection[ByteBuffer])(implicit ec: ExecutionContext): WustClient = {
    val config = ClientConfig(requestTimeoutMillis = 60 * 1000)
    val client = WebsocketClient[ByteBuffer, ApiEvent, ApiError](connection, config, handler)
    val ws = Client[ByteBuffer, Future](client)

    client.run(location)

    new WustClient(ws)
  }
}
