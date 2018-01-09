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
  val api: Api = ws.wire[Api]
  val auth: AuthApi = ws.wire[AuthApi]
}
object WustClient extends NativeWustClient

private[sdk] object WustClientFactory {
  private implicit val iShouldNotBeHere = HelpMePickle.apiEvents

  def apply(location: String, handler: IncidentHandler[ApiEvent], connection: WebsocketConnection[ByteBuffer])(implicit ec: ExecutionContext): WustClient = {
    val config = ClientConfig(requestTimeoutMillis = 60 * 1000)
    val client = WebsocketClient[ByteBuffer, ApiEvent, ApiError](connection, config, handler)
    val ws = Client[ByteBuffer, Future](client)

    client.run(location)

    new WustClient(ws)
  }
}
