package wust.framework

import java.nio.ByteBuffer

import akka.actor._
import akka.http.scaladsl.model.ws.{Message => WSMessage}
import akka.pattern.pipe
import autowire.Core.Request
import wust.framework.message._
import wust.util.time.StopWatch

import scala.concurrent.Future

trait RequestHandler[Event, PublishEvent, Failure, State] {
  case class Reaction(state: Future[State], events: Future[Seq[Event]] = Future.successful(Seq.empty))
  case class Response(reaction: Reaction, result: Future[Either[Failure, ByteBuffer]])

  // called when a client connects to the websocket. this allows for
  // managing/bookkeeping of connected clients and returning the initial state.
  // the NotifiableClient can be used to send events to downstream.
  def onClientConnect(client: NotifiableClient[PublishEvent]): Reaction

  // called when a client disconnects. this can be due to a timeout on the
  // websocket connection or the client closed the connection.
  def onClientDisconnect(client: NotifiableClient[PublishEvent], state: Future[State]): Unit

  // a request is a (path: Seq[String], args: Map[String,ByteBuffer]), which
  // needs to be mapped to a result.  if the request cannot be handled, you can
  // return an error. this is the integration point for, e.g., autowire.
  def onRequest(client: ClientIdentity, state: Future[State], request: Request[ByteBuffer]): Response

  // you can send events to the clients by calling send on the NotifiableClient.
  // here you can map the state of each client when receiving a new event.
  def onEvent(client: ClientIdentity, state: Future[State], request: PublishEvent): Reaction
}

sealed trait ClientIdentity
case class NotifiableClient[PublishEvent](actor: ActorRef) extends ClientIdentity {
  private[framework] case class Notify(event: PublishEvent)
  // def notify(origin: ClientIdentity, event: PublishEvent)(implicit ec: origin !=:= this): Unit = actor ! Notify(event)
  def notify(origin: ClientIdentity, event: PublishEvent): Unit = if (origin != this) actor ! Notify(event)
}

class ConnectedClient[Event, PublishEvent, Failure, State](
  messages: Messages[Event, Failure],
  handler: RequestHandler[Event, PublishEvent, Failure, State]) extends Actor {
  import ConnectedClient._
  import context.dispatcher
  import handler._
  import messages._

  def connected(outgoing: ActorRef) = {
    val client = new NotifiableClient[PublishEvent](self)
    def sendEvents(events: Seq[Event]) = if (events.nonEmpty) outgoing ! Notification(events.toList)

    def withState(state: Future[State]): Receive = {
      case Ping() => outgoing ! Pong()

      case CallRequest(seqId, path, args) =>
        val timer = StopWatch.started
        val response = onRequest(client, state, Request(path, args))
        import response._

        result
          .onComplete { _ => scribe.info(f"CallRequest($seqId): ${timer.readMicros}us") }

        result
          .map(r => CallResponse(seqId, r))
          .pipeTo(outgoing)

        reaction.events.foreach(sendEvents)
        context.become(withState(reaction.state))

      case client.Notify(event) =>
        val reaction = onEvent(client, state, event)
        reaction.events.foreach(sendEvents)
        context.become(withState(reaction.state))

      case Stop =>
        onClientDisconnect(client, state)
        context.stop(self)
    }

    val initial = onClientConnect(client)
    initial.events.foreach(sendEvents)
    withState(initial.state)
  }

  def receive = {
    case Connect(outgoing) => context.become(connected(outgoing))
    case Stop => context.stop(self)
  }
}
object ConnectedClient {
  case class Connect(actor: ActorRef)
  case object Stop
}
