package wust.framework

import java.nio.ByteBuffer

import akka.actor._
import akka.http.scaladsl.model.ws.Message
import akka.pattern.pipe
import autowire.Core.Request
import wust.framework.message._
import wust.util.Pipe
import wust.util.time.StopWatch

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EventSender[Event](messages: Messages[Event, _], private val actor: ActorRef) extends Comparable[EventSender[Event]] {
  import messages._

  def compareTo(other: EventSender[Event]) = actor.compareTo(other.actor)

  // possibility to send already serialized websocket messages
  def sendRaw(event: Message) = {
    scribe.info(s"--> serialized event: $event")
    actor ! event
  }

  def send(event: Event): Unit = {
    scribe.info(s"--> event: $event")
    actor ! Notification(event)
  }
}

case class RequestResult[State](state: Future[State], result: Future[ByteBuffer])

trait RequestHandler[Event, Error, State] {
  def onClientStart(sender: EventSender[Event]): Future[State]
  def onClientStop(sender: EventSender[Event], state: State): Unit

  def router(sender: EventSender[Event], state: Future[State]): PartialFunction[Request[ByteBuffer], RequestResult[State]]
  def pathNotFound(path: Seq[String]): Error
  def toError: PartialFunction[Throwable, Error]
}

class ConnectedClient[Event, Error, State](messages: Messages[Event, Error],
  handler: RequestHandler[Event, Error, State]
) extends Actor {
  import ConnectedClient._
  import handler._
  import messages._

  def connected(outgoing: ActorRef): Receive = {
    val sender = new EventSender(messages, outgoing)

    def withState(state: Future[State]): Receive = {
      case Ping() => outgoing ! Pong()
      case CallRequest(seqId, path, args) =>
        val timer = StopWatch.started
        router(sender, state).lift(Request(path, args)) match {
          case Some(RequestResult(newState, response)) =>
            response
              .map(resp => CallResponse(seqId, Right(resp)))
              .recover(toError andThen (err => CallResponse(seqId, Left(err))))
              .||>(_.onComplete { _ => scribe.info(f"CallRequest($seqId): ${timer.readMicros}us") })
              .pipeTo(outgoing)

            context.become(withState(newState))
          case None =>
            outgoing ! CallResponse(seqId, Left(pathNotFound(path)))
        }
      case Stop =>
        state.foreach(onClientStop(sender, _))
        context.stop(self)
    }

    val state = onClientStart(sender)
    withState(state)
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
