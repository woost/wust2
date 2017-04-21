package wust.framework

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.nio.ByteBuffer

import akka.NotUsed
import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.ws.{ Message, BinaryMessage }
import akka.stream.{ ActorMaterializer, OverflowStrategy }
import akka.stream.scaladsl._
import autowire.Core.{ Request, Router }
import boopickle.Default._

import message._

object WebsocketFlow {
  def apply[Event, Error, State](
    messages: Messages[Event, Error],
    handler: RequestHandler[Event, Error, State])
    (implicit system: ActorSystem): Flow[Message, Message, NotUsed] = {

    import WebsocketSerializer._
    import messages._

    val connectedClientActor = system.actorOf(Props(new ConnectedClient(messages, handler)))

    val incoming: Sink[Message, NotUsed] =
      Flow[Message].map {
        case bm: BinaryMessage.Strict =>
          val msg = deserialize[ClientMessage](bm)
          scribe.info(s"<-- $msg")
          msg
        //TODO: streamed?
      }.to(Sink.actorRef[ClientMessage](connectedClientActor, ConnectedClient.Stop))

    val outgoing: Source[Message, NotUsed] =
      Source.actorRef[Any](bufferSize = 10, overflowStrategy = OverflowStrategy.dropNew)
        .mapMaterializedValue { outActor =>
          connectedClientActor ! ConnectedClient.Connect(outActor)
          NotUsed
        }.map {
          //TODO no any, proper serialize map
          case msg: ServerMessage =>
            scribe.info(s"--> $msg")
            WebsocketSerializer.serialize(msg)
          case other: Message =>
            //we pass through already serialized websocket messages
            //in order to allow serializing once and sending to multiple clients
            other
        }

    Flow.fromSinkAndSource(incoming, outgoing)
  }
}

class WebsocketServer[Event: Pickler, Error: Pickler, State](handler: RequestHandler[Event, Error, State]) {
  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  val messages = new Messages[Event, Error]
  import messages._

  def websocketHandler = handleWebSocketMessages(WebsocketFlow(messages, handler))
  def serializedEvent(event: Event): Message = WebsocketSerializer.serialize[ServerMessage](Notification(event))

  def run(route: Route, interface: String, port: Int): Future[ServerBinding] =
    Http().bindAndHandle(route, interface = interface, port = port)
}
