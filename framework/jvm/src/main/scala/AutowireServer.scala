package framework

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Success,Failure}

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

import autowire.Core.{Request,Router}
import boopickle.Default._
import java.nio.ByteBuffer

import framework.message._, Messages._

object AutowireServer extends autowire.Server[ByteBuffer, Pickler, Pickler] {
  def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
  def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)
}

case class PathNotFoundException(path: Seq[String]) extends Exception

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

//TODO channel dependency, then subscribe, signal => action!
class ConnectedClient[CHANNEL,EVENT,AUTH,ERROR,USER](
  messages: Messages[CHANNEL,EVENT,ERROR,AUTH],
  subscribe: (ActorRef, CHANNEL) => Unit,
  router: Option[USER] => Router[ByteBuffer],
  toError: PartialFunction[Throwable,ERROR],
  authorize: AUTH => Future[Option[USER]]) extends Actor {
  import messages._, ConnectedClient._

  val notAuthenticated: Future[Option[USER]] = Future.successful(None)

  def connected(outgoing: ActorRef, user: Future[Option[USER]]): Receive = {
    case CallRequest(seqId, path, args) =>
      router(user.value.flatMap(_.toOption.flatten)).lift(Request(path, args))
        .map(_.map(resp => CallResponse(seqId, Right(resp))))
        .getOrElse(Future.failed(PathNotFoundException(path)))
        .recover(toError andThen { case err => CallResponse(seqId, Left(err)) })
        .pipeTo(outgoing)
    case ControlRequest(seqId, control) => control match {
      case Login(auth) =>
        val nextUser = authorize(auth)
        nextUser.map(_.isDefined)
          .recover { case NonFatal(_) => false }
          .map(ControlResponse(seqId, _))
          .pipeTo(outgoing)
        context.become(connected(outgoing, nextUser))
      case Logout() =>
        outgoing ! ControlResponse(seqId, true)
        context.become(connected(outgoing, notAuthenticated))
    }
    case Subscription(channel) => subscribe(outgoing, channel)
  }

  def receive = {
    case Connected(outgoing) => context.become(connected(outgoing, notAuthenticated))
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

abstract class WebsocketServer[CHANNEL: Pickler, EVENT: Pickler, ERROR: Pickler, AUTH: Pickler, USER] {
  def route: Route
  def router: Option[USER] => AutowireServer.Router
  def toError: PartialFunction[Throwable,ERROR]
  def authorize(auth: AUTH): Future[Option[USER]]

  private lazy val messages = new Messages[CHANNEL,EVENT,ERROR,AUTH]
  import messages._

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  private val dispatcher = new Dispatcher[CHANNEL,Message]

  private def newConnectedClient: Flow[Message, Message, NotUsed] = {
    val connectedClientActor = system.actorOf(Props(new ConnectedClient(messages, dispatcher.subscribe, router, toError, authorize)))

    val incoming: Sink[Message, NotUsed] =
      Flow[Message].map {
        case bm: BinaryMessage.Strict => Serializer.deserialize[ClientMessage](bm)
        //TODO: streamed?
      }.to(Sink.actorRef[ClientMessage](connectedClientActor, PoisonPill))

    val outgoing: Source[Message, NotUsed] =
      Source.actorRef[Any](10, OverflowStrategy.fail) //TODO why 10?
        .mapMaterializedValue { outActor =>
          connectedClientActor ! ConnectedClient.Connected(outActor)
          NotUsed
        }.map {
          //TODO no any, proper serialize map
          case msg: ServerMessage => Serializer.serialize(msg)
          case other: Message => other
        }

    Flow.fromSinkAndSource(incoming, outgoing)
  }

  private val commonRoute = (pathSingleSlash & get) {
    handleWebSocketMessages(newConnectedClient)
  }

  val wire = AutowireServer

  def emit(channel: CHANNEL, event: EVENT) {
    //TODO blocking serialize meh...
    val payload = Serializer.serialize[ServerMessage](Notification(event))
    dispatcher.publish(Dispatcher.ChannelEvent(channel, payload))
  }

  def run(interface: String, port: Int): Future[ServerBinding] = {
    Http().bindAndHandle(commonRoute ~ route, interface = interface, port = port)
  }
}
