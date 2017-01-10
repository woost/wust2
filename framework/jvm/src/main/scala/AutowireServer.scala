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

case class UserViewableException(msg: String) extends Exception(msg)

//TODO serializing actor
//TODO channel dependency, then subscribe, signal => action!
class ConnectedClient[CHANNEL,EVENT,AUTH,USER](
  messages: Messages[CHANNEL,EVENT,AUTH],
  subscribe: (ActorRef, CHANNEL) => Unit,
  router: Option[USER] => Router[ByteBuffer],
  authorize: AUTH => Future[USER]) extends Actor {
  import messages._

  object NotAuthenticated extends Exception("not authenticated")
  val unauthorized = Future.failed(NotAuthenticated)

  //TODO different Receive for loggedin and loggedout? context.become...
  def connected(outgoing: ActorRef, user: Future[USER]): Receive = {
    case CallRequest(seqId, path, args) =>
      router(user.value.flatMap(_.toOption))
        .lift(Request(path, args))
        .map(_.map(Response(seqId, _)))
        .getOrElse(Future.successful(BadRequest(seqId, s"no route for request: $path")))
        .recover {
          case UserViewableException(msg) => BadRequest(seqId, s"error: $msg")
          case NonFatal(_) => BadRequest(seqId, "internal server error")
        }.map(Serializer.serialize[ServerMessage](_))
        .pipeTo(outgoing)
    case Subscription(channel) => subscribe(outgoing, channel)
    case Login(auth) =>
      val nextUser = authorize(auth)
      nextUser
        .map(_ => LoggedIn())
        .recover { case NonFatal(e) => LoginFailed(e.getMessage) }
        .map(Serializer.serialize[ServerMessage](_))
        .pipeTo(outgoing)
      context.become(connected(outgoing, nextUser))
    //TODO a future login will respond with LoggedIn even if there was a logout in between
    // client sends: Login, Logout. sees: LoggedOut, LoggedIn. actor state: LoggedOut
    case Logout() =>
      outgoing ! Serializer.serialize[ServerMessage](LoggedOut())
      context.become(connected(outgoing, unauthorized))
  }

  def receive = {
    case ConnectedClient.Connected(outgoing) => context.become(connected(outgoing, unauthorized))
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

//TODO independent of autowire
abstract class WebsocketServer[CHANNEL: Pickler, EVENT: Pickler, AUTH: Pickler, USER] {
  def route: Route
  def router: Option[USER] => AutowireServer.Router
  def authorize(auth: AUTH): Future[USER]

  private lazy val messages = new Messages[CHANNEL,EVENT,AUTH]
  import messages._

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  private val dispatcher = new Dispatcher[CHANNEL,Message]

  private def newConnectedClient: Flow[Message, Message, NotUsed] = {
    val connectedClientActor = system.actorOf(Props(new ConnectedClient(messages, dispatcher.subscribe, router, authorize)))

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
