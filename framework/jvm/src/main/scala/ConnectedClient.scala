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

trait RequestHandler[Event, PublishEvent, Error, State] {
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

  // a request can return events, here you can distribute the events to all connected clients.
  // e.g., you might keep a list of connected clients in the onClientConnect/onClientDisconnect and then distribute the event to all of them.
  def publishEvents(origin: EventSender[PublishEvent], events: Seq[Event]): Unit

  // whenever there is a new incoming event arrive, this method will be called.
  // events can trigger further events to provide missing data for the client
  // or to filter out certain events. Events have to be explicitly forwarded.
  // for example, returning Seq.empty will ignore the event.
  def transformIncomingEvent(event: PublishEvent, state: State): Future[Seq[Event]]

  // whenever there was an interaction with the client, the state might have changed.
  // either the stateholder recorded a new state during a request or there were events.
  // this does not mean, that the states are different; as we do not make assumption about state equality.
  // here you can return additional events to be sent to the client.
  def onClientInteraction(prevState: State, state: State): Future[Seq[Event]]

  // when incoming or self-emitted events are received, they can be applied to the state.
  // here you can return a new state.
  def applyEventsToState(event: Seq[Event], state: State): State

  // called when a client connects to the websocket.
  // this allows for managing/bookkeeping of connected clients.
  // the eventsender can be used to send events to downstream
  def onClientConnect(sender: EventSender[PublishEvent], state: State): Unit

  // called when a client disconnects.
  // this can be due to a timeout on the websocket connection or the client closed the connection.
  def onClientDisconnect(sender: EventSender[PublishEvent], state: State): Unit
}

class EventSender[PublishEvent](private val actor: ActorRef) {
  private[framework] case class Notify(event: PublishEvent)

  def send(event: PublishEvent): Unit = actor ! Notify(event)

  override def equals(other: Any) = other match {
    case other: EventSender[_] => actor.equals(other.actor)
    case _ => false
  }

  override def hashCode = actor.hashCode
}

class ConnectedClient[Event, PublishEvent, Error, State](
  messages: Messages[Event, Error],
  handler: RequestHandler[Event, PublishEvent, Error, State]) extends Actor {
  import ConnectedClient._
  import handler._
  import messages._

  import context.dispatcher

  def connected(outgoing: ActorRef): Receive = {
    val sender = new EventSender[PublishEvent](self)

    def withState(state: Future[State]): Receive = {
      case Ping() => outgoing ! Pong()
      case CallRequest(seqId, path, args) =>
        val validatedState = state.map(validate)
        val timer = StopWatch.started
        val holder = new StateHolder[State, Event](validatedState)
        onRequest(holder, Request(path, args)) match {
          case Right(response) =>
            response
              .map(resp => CallResponse(seqId, Right(resp)))
              .recover(toError andThen (err => CallResponse(seqId, Left(err))))
              .sideEffect(_.onComplete { _ => scribe.info(f"CallRequest($seqId): ${timer.readMicros}us") })
              .pipeTo(outgoing)

            holder.events.foreach { events =>
              // sideeffect: publish event to others
              if (events.nonEmpty) publishEvents(sender, events)
            }

            val (newState, events) = switchState(state, holder.state)
            sendEvents(events)
            context.become(withState(newState))

          case Left(error) =>
            outgoing ! CallResponse(seqId, Left(error))
        }

      case sender.Notify(event) =>
        val validatedState = state.map(validate)
        val newEvents = for {
          validatedState <- validatedState
          events <- transformIncomingEvent(event, validatedState)
        } yield events

        val (newState, events) = switchState(state, validatedState, newEvents)
        sendEvents(events)
        context.become(withState(newState))

      case Stop =>
        state.foreach(onClientDisconnect(sender, _))
        context.stop(self)
    }

    def switchState(state: Future[State], newState: Future[State], initialEvents: Future[Seq[Event]] = Future.successful(Seq.empty)): (Future[State], Future[Seq[Event]]) = {
      val events = for {
        state <- state
        newState <- newState
        initialEvents <- initialEvents
        additionalEvents <- onClientInteraction(state, newState)
      } yield initialEvents ++ additionalEvents

      val switchState = for {
        state <- newState
        events <- events
      } yield if (events.isEmpty) state else applyEventsToState(events, state)

      (switchState, events)
    }

    def sendEvents(events: Future[Seq[Event]]) = {
      events.foreach { events =>
        // sideeffect: send actual event to client
        if (events.nonEmpty) outgoing ! Notification(events.toList)
      }
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
