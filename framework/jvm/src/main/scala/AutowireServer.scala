package framework

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.NotUsed
import akka.event.{LookupClassification, EventBus}
import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.ws.{Message, BinaryMessage}
import akka.pattern.pipe
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.util.ByteString

import autowire.Core.Request
import boopickle.Default._
import java.nio.ByteBuffer

import framework.message._, Messages._

object AutowireServer extends autowire.Server[ByteBuffer, Pickler, Pickler] {
  def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
  def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)
}

class Dispatcher[CHANNEL,PAYLOAD] extends EventBus with LookupClassification {
  import Dispatcher._

  type Event = ChannelEvent[CHANNEL,PAYLOAD]
  type Classifier = CHANNEL
  type Subscriber = ActorRef

  protected def classify(event: Event): Classifier = event.channel
  protected def publish(event: Event, subscriber: Subscriber): Unit = subscriber ! event.payload
  protected def compareSubscribers(a: Subscriber, b: Subscriber): Int = a.compareTo(b)
  protected def mapSize: Int = 128 // expected size of classifiers
}
object Dispatcher {
  case class ChannelEvent[CHANNEL,PAYLOAD](channel: CHANNEL, payload: PAYLOAD)
}

class ConnectedClient[CHANNEL,EVENT,PAYLOAD](
  messages: Messages[CHANNEL,EVENT],
  subscribe: (ActorRef, CHANNEL) => Unit,
  request: (SequenceId, Seq[String], Map[String, ByteBuffer]) => Future[PAYLOAD]) extends Actor {
  import messages._

  def connected(outgoing: ActorRef): Receive = {
    case CallRequest(seqId, path, args) => request(seqId, path, args).pipeTo(outgoing)
    case Subscription(channel) => subscribe(outgoing, channel)
  }

  def receive = {
    case ConnectedClient.Connected(outgoing) => context.become(connected(outgoing))
  }
}
object ConnectedClient {
  case class Connected(actor: ActorRef)
}

object Serializer {
  def serialize[T : Pickler](msg: T): Message = {
    println(s"--> $msg")
    val bytes = Pickle.intoBytes(msg)
    BinaryMessage(ByteString(bytes))
  }

  def deserialize[T : Pickler](bm: BinaryMessage.Strict): T = {
    val bytes = bm.getStrictData.asByteBuffer
    val msg = Unpickle[T].fromBytes(bytes)
    println(s"<-- $msg")
    msg
  }
}

case class UserViewableException(msg: String) extends Exception(msg)

abstract class WebsocketServer[CHANNEL: Pickler,EVENT: Pickler] {
  def route: Route
  def router: AutowireServer.Router

  private val messages = new Messages[CHANNEL,EVENT]
  import messages._

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  private val dispatcher = new Dispatcher[CHANNEL,Message]

  private def onRequest(seqId: SequenceId, path: Seq[String], args: Map[String,ByteBuffer]): Future[Message] = {
    router.lift(Request(path, args))
      .map(_.map(Response(seqId, _)))
      .getOrElse(Future.successful(BadRequest(seqId, s"no route for request: $path")))
      .recover {
        case UserViewableException(msg) => BadRequest(seqId, s"error: $msg")
        case NonFatal(_) => BadRequest(seqId, "internal server error")
      }
      .map(Serializer.serialize[ServerMessage](_))
  }

  private def newConnectedClient: Flow[Message, Message, NotUsed] = {
    val connectedClientActor = system.actorOf(Props(new ConnectedClient(messages, dispatcher.subscribe, onRequest)))

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case bm: BinaryMessage.Strict => Serializer.deserialize[ClientMessage](bm)
        //TODO: streamed?
      }.to(Sink.actorRef[ClientMessage](connectedClientActor, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[Message](10, OverflowStrategy.fail) //TODO why 10?
        .mapMaterializedValue { outActor =>
          connectedClientActor ! ConnectedClient.Connected(outActor)
          NotUsed
        }

    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  private val commonRoute = (pathSingleSlash & get) {
    handleWebSocketMessages(newConnectedClient)
  }

  val wire = AutowireServer

  def emit(channel: CHANNEL, event: EVENT) = {
    val payload = Serializer.serialize[ServerMessage](Notification(event))
    dispatcher.publish(Dispatcher.ChannelEvent(channel, payload))
  }

  def run(interface: String, port: Int): Future[ServerBinding] = {
    Http().bindAndHandle(commonRoute ~ route, interface = interface, port = port)
  }
}
