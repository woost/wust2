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
  def apply[Channel, Event, Error, Token, State](
    messages: Messages[Channel, Event, Error, Token],
    handler: RequestHandler[Event, Error, Token, State],
    dispatcher: Dispatcher[Channel, Event])(implicit system: ActorSystem): Flow[Message, Message, NotUsed] = {

    import WebsocketSerializer._
    import messages._

    val connectedClientActor = system.actorOf(Props(new ConnectedClient(messages, handler, dispatcher)))

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
          case other: Message => other
        }

    Flow.fromSinkAndSource(incoming, outgoing)
  }
}

class WebsocketServer[Channel: Pickler, Event: Pickler, Error: Pickler, Token: Pickler, State](
    handler: RequestHandler[Event, Error, Token, State]) {

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  val messages = new Messages[Channel, Event, Error, Token]
  private val dispatcher = new EventDispatcher(messages)

  val emit = dispatcher.emit _
  def websocketHandler = handleWebSocketMessages(WebsocketFlow(messages, handler, dispatcher))

  def run(route: Route, interface: String, port: Int): Future[ServerBinding] = {
    Http().bindAndHandle(route, interface = interface, port = port)
  }
}
