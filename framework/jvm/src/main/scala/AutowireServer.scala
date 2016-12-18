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

trait WebsocketServer[CHANNEL,EVENT] {
  implicit val channelPickler: Pickler[CHANNEL]
  implicit val eventPickler: Pickler[EVENT]
  val router: AutowireServer.Router

  val wire = AutowireServer

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  private class Dispatcher extends Actor {
    //TODO map of connectedClients
    import scala.collection.mutable
    val connectedClients = mutable.Map.empty[CHANNEL, mutable.Set[ActorRef]].withDefaultValue(mutable.Set.empty)

    def receive = {
      case Subscribe(bytes) =>
        //TODO: message should not have bytebuffer, be generic (see messages.scala)
        val channel = Unpickle[CHANNEL].fromBytes(bytes)
        bytes.flip()
        connectedClients(channel) += sender()
        context.watch(sender()) // emits terminated when sender disconnects
      case Terminated(sender) =>
        connectedClients.values.foreach(_ -= sender)
      case n@Notification(bytes, _) =>
        //TODO: message should not have bytebuffer, be generic (see messages.scala)
        val channel = Unpickle[CHANNEL].fromBytes(bytes)
        bytes.flip()
        connectedClients(channel).foreach(_ ! n)
    }
  }

  private val dispatchActor = system.actorOf(Props(new Dispatcher))

  private case class Connected(actor: ActorRef)

  private class ConnectedClient extends Actor {
    private def connected(outgoing: ActorRef): Receive = {
      {
        case CallRequest(seqId, path, args) =>
          val response = router(Request(path, args)).map { result =>
            Response(seqId, result)
          }
          response.foreach(outgoing ! _) // TODO better something like mapasync in flow?

        case n: Subscribe => dispatchActor ! n
        case n: Notification => outgoing ! n
      }
    }

    def receive = {
      case Connected(outgoing) => context.become(connected(outgoing))
    }
  }

  private def newConnectedClient: Flow[Message, Message, NotUsed] = {
    implicit val clientMessagePickler = ClientMessage.pickler
    implicit val serverMessagePickler = ServerMessage.pickler

    val connectedClientActor = system.actorOf(Props(new ConnectedClient))

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case bm: BinaryMessage if bm.isStrict =>
          val buffer = bm.getStrictData.asByteBuffer
          Unpickle[ClientMessage].fromBytes(buffer)
      }.to(Sink.actorRef[ClientMessage](connectedClientActor, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[ServerMessage](10, OverflowStrategy.fail) //TODO why 10?
        .mapMaterializedValue { outActor =>
          connectedClientActor ! Connected(outActor)
          NotUsed
        }.map { (outMsg: ServerMessage) =>
          val bytes = Pickle.intoBytes(outMsg)
          BinaryMessage(ByteString(bytes))
        }

    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  private val commonRoute = (path("ws") & get) { // TODO on root or configurable?
    handleWebSocketMessages(newConnectedClient)
  }

  def emit(channel: CHANNEL, event: EVENT) {
    val eventBytes = Pickle.intoBytes(event)
    val channelBytes = Pickle.intoBytes(channel)
    dispatchActor ! Notification(channelBytes, eventBytes)
  }

  val route: Route

  def run(interface: String, port: Int): Future[ServerBinding] = {
    Http().bindAndHandle(commonRoute ~ route, interface = interface, port = port)
  }
}
