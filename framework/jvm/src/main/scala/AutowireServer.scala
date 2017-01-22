package framework

import scala.concurrent.ExecutionContext.Implicits.global //TODO
import scala.concurrent.Future
import scala.util.control.NonFatal
import java.nio.ByteBuffer

import akka.NotUsed
import akka.event.{LookupClassification, EventBus}
import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.ws.{Message, BinaryMessage}
import akka.pattern.pipe
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.util.ByteString
import autowire.Core.{Request, Router}
import boopickle.Default._
import com.outr.scribe._

import framework.message._

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

case class PathNotFoundException(path: Seq[String]) extends Exception
//TODO channel dependency, then subscribe, signal => action!
class ConnectedClient[CHANNEL,AUTH,ERROR,USER](
  messages: Messages[CHANNEL,_,ERROR,AUTH],
  dispatcher: Dispatcher[CHANNEL,_],
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
    case Subscription(channel) => dispatcher.subscribe(outgoing, channel)
    case Stop =>
      dispatcher.unsubscribe(outgoing)
      context.stop(self)
  }

  def receive = {
    case Connect(outgoing) => context.become(connected(outgoing, notAuthenticated))
    case Stop => context.stop(self)
  }
}
object ConnectedClient {
  case class Connect(actor: ActorRef)
  case object Stop
}

object Serializer {
  def serialize[T : Pickler](msg: T): Message = {
    val bytes = Pickle.intoBytes(msg)
    BinaryMessage(ByteString(bytes))
  }

  def deserialize[T : Pickler](bm: BinaryMessage.Strict): T = {
    val bytes = bm.getStrictData.asByteBuffer
    val msg = Unpickle[T].fromBytes(bytes)
    msg
  }
}

abstract class WebsocketServer[CHANNEL: Pickler, EVENT: Pickler, ERROR: Pickler, AUTH: Pickler, USER] {
  def route: Route
  def router(user: Option[USER]): AutowireServer.Router
  def toError: PartialFunction[Throwable,ERROR]
  def authorize(auth: AUTH): Future[Option[USER]]

  private lazy val messages = new Messages[CHANNEL,EVENT,ERROR,AUTH]
  import messages._

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  private val dispatcher = new Dispatcher[CHANNEL,Message]

  private def newConnectedClient: Flow[Message, Message, NotUsed] = {
    val connectedClientActor = system.actorOf(Props(new ConnectedClient(messages, dispatcher, router, toError, authorize)))

    val incoming: Sink[Message, NotUsed] =
      Flow[Message].map {
        case bm: BinaryMessage.Strict =>
          val msg = Serializer.deserialize[ClientMessage](bm)
          logger.info(s"<-- $msg")
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
            logger.info(s"--> $msg")
            Serializer.serialize(msg)
          case other: Message => other
        }

    Flow.fromSinkAndSource(incoming, outgoing)
  }

  private val commonRoute = (pathSingleSlash & get) {
    handleWebSocketMessages(newConnectedClient)
  }

  def emit(channel: CHANNEL, event: EVENT): Unit = Future {
    logger.info(s"-[$channel]-> event: $event")
    val payload = Serializer.serialize[ServerMessage](Notification(event))
    dispatcher.publish(Dispatcher.ChannelEvent(channel, payload))
  }

  val wire = AutowireServer

  def run(interface: String, port: Int): Future[ServerBinding] = {
    Http().bindAndHandle(commonRoute ~ route, interface = interface, port = port)
  }
}
