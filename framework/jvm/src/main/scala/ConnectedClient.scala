package wust.framework

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import java.nio.ByteBuffer

import akka.actor._
import akka.pattern.pipe
import autowire.Core.Request

import wust.util.time.StopWatch
import wust.util.Pipe
import message._

trait RequestHandler[Event, Error, Token, State] {
  def router(state: Future[State]): PartialFunction[Request[ByteBuffer], (Future[State], Future[ByteBuffer])]
  def pathNotFound(path: Seq[String]): Error
  def toError: PartialFunction[Throwable, Error]

  def initialState: Future[State]
  def authenticate(state: Future[State], token: Token): Future[Option[State]]
  def onStateChange(state: State): Seq[Future[Event]]
}

class ConnectedClient[Channel, Event, Error, Token, State](
  messages: Messages[Channel, Event, Error, Token],
  handler: RequestHandler[Event, Error, Token, State],
  dispatcher: Dispatcher[Channel, Event]
) extends Actor {

  import ConnectedClient._
  import messages._, handler._

  def connected(outgoing: ActorRef, state: Future[State] = initialState): Receive = {
      case Ping() => outgoing ! Pong()
      case CallRequest(seqId, path, args) =>
        router(state).lift(Request(path, args)) match {
          case Some((newState, response)) =>
            val timer = StopWatch.started
            response
              //TODO: transform = map + recover?
              .map(resp => CallResponse(seqId, Right(resp)))
              .recover(toError andThen { case err => CallResponse(seqId, Left(err)) })
              .||>(_.onComplete { _ => scribe.info(f"CallRequest($seqId): ${timer.readMicros}us") })
              .pipeTo(outgoing)

            for {
              state <- state
              newState <- newState
              // this assumes equality on the state type or state being the same instance
              // only notify if the state actually changed in this request
              if state != newState
            } onStateChange(newState).foreach(_.map(Notification.apply).pipeTo(outgoing))

            context.become(connected(outgoing, newState))
          case None =>
            outgoing ! CallResponse(seqId, Left(pathNotFound(path)))
        }
      case ControlRequest(seqId, control) => control match {
        case Login(token) =>
          val newState = authenticate(state, token)
          newState
            .map(newState => ControlResponse(seqId, newState.isDefined))
            .pipeTo(outgoing)

          newState.foreach(_.foreach(onStateChange _))

          val currentState = for {
            state <- state
            newState <- newState
          } yield newState.getOrElse(state)

          context.become(connected(outgoing, currentState))
        case Logout() =>
          outgoing ! ControlResponse(seqId, true)
          context.become(connected(outgoing, initialState))
        case Subscribe(channel) =>
          dispatcher.subscribe(outgoing, channel)
          outgoing ! ControlResponse(seqId, true)
        case Unsubscribe(channel) =>
          dispatcher.unsubscribe(outgoing, channel)
          outgoing ! ControlResponse(seqId, true)
      }
      case Stop =>
        dispatcher.unsubscribe(outgoing)
        context.stop(self)
  }

  def receive = {
    case Connect(outgoing) => context.become(connected(outgoing, initialState))
    case Stop => context.stop(self)
  }
}
object ConnectedClient {
  case class Connect(actor: ActorRef)
  case object Stop
}
