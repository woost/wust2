package framework

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.NotUsed
import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model._
import akka.pattern.pipe
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.util.ByteString

import autowire.Core.Request
import boopickle.Default._
import java.nio.ByteBuffer

import framework.message._

object AutowireServer extends autowire.Server[ByteBuffer, Pickler, Pickler] {
  def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
  def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)
}

trait Registrar[CHANNEL] {
  def subscribe(channel: CHANNEL, sender: ActorRef): Unit
}

trait Dispatcher[CHANNEL,EVENT] extends Registrar[CHANNEL] {
  def notify(channel: CHANNEL, event: EVENT): Unit
}

class DispatcherImpl[CHANNEL,EVENT] extends Dispatcher[CHANNEL,EVENT] with TypedActor.Receiver {
  import scala.collection.mutable
  private val connectedClients = mutable.Map.empty[CHANNEL, mutable.Set[ActorRef]].withDefaultValue(mutable.Set.empty)

  def subscribe(channel: CHANNEL, sender: ActorRef) {
    connectedClients(channel) += sender
    TypedActor.context.watch(sender) // emits terminated when sender disconnects
  }

  def notify(channel: CHANNEL, event: EVENT) {
    val notification = Notification(channel, event)
    connectedClients(channel).foreach(_ ! notification)
  }

  def onReceive(message: Any, sender: ActorRef): Unit = message match {
    case Terminated(actor) => connectedClients.values.foreach(_ -= actor)
    case _ =>
  }
}

class ConnectedClient[CHANNEL](registrar: Registrar[CHANNEL], router: AutowireServer.Router) extends Actor {
  private def connected(outgoing: ActorRef): Receive = {
    case CallRequest(seqId, path, args) =>
      val response = router(Request(path, args)).map(Response(seqId, _))
      response.foreach(outgoing ! _)
    case Subscribe(channel: CHANNEL) => registrar.subscribe(channel, outgoing)
  }

  def receive = {
    case ConnectedClient.Connected(outgoing) => context.become(connected(outgoing))
  }
}
object ConnectedClient {
  case class Connected(actor: ActorRef)
}

trait WebsocketServer[CHANNEL,EVENT] {
  implicit def channelPickler: Pickler[CHANNEL]
  implicit def eventPickler: Pickler[EVENT]
  implicit def clientMessagePickler = ClientMessage.pickler[CHANNEL]
  implicit def serverMessagePickler = ServerMessage.pickler[CHANNEL, EVENT]

  def route: Route
  def router: AutowireServer.Router

  val wire = AutowireServer

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  private val dispatcher: Dispatcher[CHANNEL,EVENT] = TypedActor(system).typedActorOf(TypedProps[DispatcherImpl[CHANNEL,EVENT]])

  def newConnectedClient: Flow[Message, Message, NotUsed] = {
    val connectedClientActor = system.actorOf(Props(new ConnectedClient(dispatcher, router)))

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case bm: BinaryMessage if bm.isStrict =>
          val buffer = bm.getStrictData.asByteBuffer
          Unpickle[ClientMessage].fromBytes(buffer)
      }.to(Sink.actorRef[ClientMessage](connectedClientActor, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[ServerMessage](10, OverflowStrategy.fail) //TODO why 10?
        .mapMaterializedValue { outActor =>
          connectedClientActor ! ConnectedClient.Connected(outActor)
          NotUsed
        }.map { outMsg =>
          val bytes = Pickle.intoBytes(outMsg)
          BinaryMessage(ByteString(bytes))
        }

    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  private val commonRoute = (path("ws") & get) { // TODO on root or configurable?
    handleWebSocketMessages(newConnectedClient)
  }

  def emit(channel: CHANNEL, event: EVENT) {
    dispatcher.notify(channel, event)
  }

  def run(interface: String, port: Int): Future[ServerBinding] = {
    Http().bindAndHandle(commonRoute ~ route, interface = interface, port = port)
  }
}
