package framework

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.nio.ByteBuffer

import akka.NotUsed
import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.ws.{Message, BinaryMessage}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.util.ByteString
import autowire.Core.{Request, Router}
import boopickle.Default._

import framework.message._

object AutowireServer extends autowire.Server[ByteBuffer, Pickler, Pickler] {
  def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
  def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)
}

object Serializer {
  def serialize[T: Pickler](msg: T): Message = {
    val bytes = Pickle.intoBytes(msg)
    BinaryMessage(ByteString(bytes))
  }

  def deserialize[T: Pickler](bm: BinaryMessage.Strict): T = {
    val bytes = bm.getStrictData.asByteBuffer
    val msg = Unpickle[T].fromBytes(bytes)
    msg
  }
}

abstract class WebsocketServer[CHANNEL: Pickler, EVENT: Pickler, ERROR: Pickler, AUTH: Pickler, USER] {
  def route: Route
  def router(user: Option[USER]): AutowireServer.Router
  def pathNotFound(path: Seq[String]): ERROR
  def toError: PartialFunction[Throwable, ERROR]
  def authorize(auth: AUTH): Future[Option[USER]]

  private lazy val messages = new Messages[CHANNEL, EVENT, ERROR, AUTH]
  import messages._

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  private val dispatcher = new Dispatcher[CHANNEL, Message]

  private def newConnectedClient: Flow[Message, Message, NotUsed] = {
    val connectedClientActor = system.actorOf(Props(new ConnectedClient(messages, dispatcher, router, pathNotFound, toError, authorize)))

    val incoming: Sink[Message, NotUsed] =
      Flow[Message].map {
        case bm: BinaryMessage.Strict =>
          val msg = Serializer.deserialize[ClientMessage](bm)
          scribe.info(s"<-- $msg")
          msg
        //TODO: streamed?
      }.to(Sink.actorRef[ClientMessage](connectedClientActor, ConnectedClient.Stop))

    val outgoing: Source[Message, NotUsed] =
      Source.actorRef[Any](10, OverflowStrategy.fail) //TODO why 10?
        .mapMaterializedValue { outActor =>
          connectedClientActor ! ConnectedClient.Connect(outActor)
          NotUsed
        }.map {
          //TODO no any, proper serialize map
          case msg: ServerMessage =>
            scribe.info(s"--> $msg")
            Serializer.serialize(msg)
          case other: Message => other
        }

    Flow.fromSinkAndSource(incoming, outgoing)
  }

  protected def websocketHandler = handleWebSocketMessages(newConnectedClient)

  def emit(channel: CHANNEL, event: EVENT): Unit = Future {
    scribe.info(s"-[$channel]-> event: $event")
    val payload = Serializer.serialize[ServerMessage](Notification(event))
    dispatcher.publish(Dispatcher.ChannelEvent(channel, payload))
  }

  val wire = AutowireServer

  def run(interface: String, port: Int): Future[ServerBinding] = {
    Http().bindAndHandle(route, interface = interface, port = port)
  }
}
