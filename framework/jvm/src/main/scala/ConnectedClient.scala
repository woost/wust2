package wust.framework

import java.nio.ByteBuffer

import akka.actor._
import akka.http.scaladsl.model.ws.{ Message => WSMessage }
import akka.pattern.pipe
import autowire.Core.Request
import wust.framework.message._
import wust.util.Pipe
import wust.util.time.StopWatch
import wust.framework.state.StateHolder

import scala.concurrent.Future

trait RequestHandler[Event, Error, State] {
  // initial state for a new client
  def initialState: State

  // a new request from the client or a new upstream event has arrived.
  // the validate method allows to sanitize the state, e.g., for expiring the authentication before actually handling the request or event.
  def validate(state: State): State

  // a request is a (path: Seq[String], args: Map[String,ByteBuffer]), which needs to be mapped to a result.
  // if the request cannot be handled, you can return an error.
  // this is the integration point for, e.g., autowire.
  def onRequest(holder: StateHolder[State, Event], request: Request[ByteBuffer]): Either[Error, Future[ByteBuffer]]

  // any exception that is thrown in your request handler is catched and can be mapped to an error type.
  // remaining throwables will be thrown!
  def toError: PartialFunction[Throwable, Error]

  // a request can return events, here you can distribute the event to all connected clients.
  // e.g., you might keep a list of connected clients in the onClientConnect/onClientDisconnect and then distribute the event to all of them.
  def publishEvent(event: Event): Unit

  // events can trigger further events to provide
  // missing data for the client. Events have to be explicitly forwarded.
  // for example, returning Seq.empty will ignore the event.
  def triggeredEvents(event: Event, state: State): Future[Seq[Event]]

  // after you have allowed the event, you can then adapt the state according to the event
  def onEvent(event: Event, state: State): State

  // called when a client connects to the websocket.
  // this allows for managing/bookkeeping of connected clients.
  // the eventsender can be used to send events to downstream
  def onClientConnect(sender: EventSender[Event], state: State): Unit

  // called when a client disconnects.
  // this can be due to a timeout on the websocket connection or the client closed the connection.
  def onClientDisconnect(sender: EventSender[Event], state: State): Unit

  // whenever there was an interaction with the client, the state might have changed.
  // either the stateholder recorded a new state during a request or the onEvent method was called.
  // this does not mean, that the states are different; as we do not make assumption about state equality.
  def onClientInteraction(sender: EventSender[Event], prevState: State, state: State): Unit
}

class EventSender[Event](messages: Messages[Event, _], private val actor: ActorRef) {
  import messages._

  def send(event: Event): Unit = {
    actor ! Notification(event)
  }

  override def equals(other: Any) = other match {
    case other: EventSender[_] => actor.equals(other.actor)
    case _ => false
  }

  override def hashCode = actor.hashCode
}

class ConnectedClient[Event, Error, State](
  messages: Messages[Event, Error],
  handler: RequestHandler[Event, Error, State]) extends Actor {
  import ConnectedClient._
  import handler._
  import messages._

  import context.dispatcher

  def connected(outgoing: ActorRef): Receive = {
    val sender = new EventSender(messages, self)

    def withState(state: Future[State]): Receive = {
      case Ping() => outgoing ! Pong()
      case CallRequest(seqId, path, args) =>
        val newState = state.map(validate)
        val timer = StopWatch.started
        val holder = new StateHolder[State, Event](newState)
        onRequest(holder, Request(path, args)) match {
          case Right(response) =>
            response
              .map(resp => CallResponse(seqId, Right(resp)))
              .recover(toError andThen (err => CallResponse(seqId, Left(err))))
              .sideEffect(_.onComplete { _ => scribe.info(f"CallRequest($seqId): ${timer.readMicros}us") })
              .pipeTo(outgoing)

            holder.events.foreach(_.foreach(publishEvent))
            switchState(state, holder.state)
          case Left(error) =>
            outgoing ! CallResponse(seqId, Left(error))
        }

      case Notification(event) =>
        val newState = for {
          unvalidatedState <- state
          validatedState = validate(unvalidatedState)
          events <- triggeredEvents(event, validatedState)
        } yield {
          events.foldLeft(validatedState) { (state, event) =>
            // sideeffect: send actual event to client
            outgoing ! Notification(event)
            onEvent(event, state)
          }
        }

        switchState(state, newState)

      case Stop =>
        state.foreach(onClientDisconnect(sender, _))
        context.stop(self)
    }

    def switchState(state: Future[State], newState: Future[State]) {
      for {
        state <- state
        newState <- newState
      } onClientInteraction(sender, state, newState)

      context.become(withState(newState))
    }

    val state = initialState
    onClientConnect(sender, state)
    withState(Future.successful(state))
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
