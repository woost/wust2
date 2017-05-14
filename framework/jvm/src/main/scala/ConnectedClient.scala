package wust.framework

import java.nio.ByteBuffer

import akka.actor._
import akka.http.scaladsl.model.ws.{Message => WSMessage}
import akka.pattern.pipe
import autowire.Core.Request
import wust.framework.message._
import wust.util.Pipe
import wust.util.time.StopWatch
import wust.framework.state.StateHolder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EventSender[Event](messages: Messages[Event, _], private val actor: ActorRef) extends Comparable[EventSender[Event]] {
  import messages._

  def compareTo(other: EventSender[Event]) = actor.compareTo(other.actor)

  def send(event: Event): Unit = {
    scribe.info(s"--> event: $event")
    actor ! Notification(event)
  }
}

trait RequestHandler[Event, Error, State] {
  def onClientStart(sender: EventSender[Event]): Future[State]
  def onClientStop(sender: EventSender[Event], state: State): Unit

  def before(state: Future[State]): Future[State]
  def after(prevState: Future[State], state: Future[State]): Future[Seq[Future[Event]]]
  def router(holder: StateHolder[State, Event]): PartialFunction[Request[ByteBuffer], Future[ByteBuffer]]
  def isEventAllowed(event: Event, state: Future[State]): Future[Boolean]
  def publishEvent(event: Event): Unit
  def pathNotFound(path: Seq[String]): Error
  def toError: PartialFunction[Throwable, Error]
}

class ConnectedClient[Event, Error, State](
    messages: Messages[Event, Error],
    handler:  RequestHandler[Event, Error, State]) extends Actor {
  import ConnectedClient._
  import handler._
  import messages._

  def connected(outgoing: ActorRef): Receive = {
    val sender = new EventSender(messages, self)

    def withState(state: Future[State]): Receive = {
      case Ping() => outgoing ! Pong()
      case CallRequest(seqId, path, args) =>
        val newState = before(state)
        val timer = StopWatch.started
        val holder = new StateHolder(newState, publishEvent)
        router(holder).lift(Request(path, args)) match {
          case Some(response) =>
            response
              .map(resp => CallResponse(seqId, Right(resp)))
              .recover(toError andThen (err => CallResponse(seqId, Left(err))))
              .||>(_.onComplete { _ => scribe.info(f"CallRequest($seqId): ${timer.readMicros}us") })
              .pipeTo(outgoing)

            val newState = holder.state
            after(state, newState).foreach(_.foreach(_.map(Notification.apply).pipeTo(outgoing)))
            context.become(withState(newState))
          case None =>
            outgoing ! CallResponse(seqId, Left(pathNotFound(path)))
        }
      case Notification(event) =>
        val newState = before(state)
        isEventAllowed(event, newState).foreach { allowed =>
          if (allowed) outgoing ! Notification(event)
        }

        after(state, newState).foreach(_.foreach(_.map(Notification.apply).pipeTo(outgoing)))
        context.become(withState(newState))
      case Stop =>
        state.foreach(onClientStop(sender, _))
        context.stop(self)
    }

    val state = onClientStart(sender)
    withState(state)
  }

  def receive = {
    case Connect(outgoing) => context.become(connected(outgoing))
    case Stop              => context.stop(self)
  }
}
object ConnectedClient {
  case class Connect(actor: ActorRef)
  case object Stop
}
