package wust.framework

import akka.NotUsed
import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import boopickle.Default._
import wust.framework.message._

import scala.concurrent.Future

object WebsocketFlow {
  def apply[Event, PublishEvent, Failure, State](
    messages: Messages[Event, Failure],
    handler: RequestHandler[Event, PublishEvent, Failure, State])
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
      Source.actorRef[ServerMessage](bufferSize = 10, overflowStrategy = OverflowStrategy.dropNew)
        .mapMaterializedValue { outActor =>
          connectedClientActor ! ConnectedClient.Connect(outActor)
          NotUsed
        }.map { msg =>
          // scribe.info(s"--> to client: $msg")
          WebsocketSerializer.serialize(msg)
        }

    Flow.fromSinkAndSource(incoming, outgoing)
  }
}

class WebsocketServer[Event: Pickler, PublishEvent, Failure: Pickler, State](handler: RequestHandler[Event, PublishEvent, Failure, State]) {
  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  val messages = new Messages[Event, Failure]
  def websocketHandler = handleWebSocketMessages(WebsocketFlow(messages, handler))

  def run(route: Route, interface: String, port: Int): Future[ServerBinding] =
    Http().bindAndHandle(route, interface = interface, port = port)
}
