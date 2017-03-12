package wust.framework

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import java.nio.ByteBuffer

import akka.actor._
import akka.pattern.pipe
import autowire.Core.{ Request, Router }

import wust.util.time.StopWatch
import wust.util.Pipe
import message._

trait RequestHandler[Channel, Event, Error, AuthToken, User] {
  def router(user: Option[User]): AutowireServer.Router
  def pathNotFound(path: Seq[String]): Error
  def toError: PartialFunction[Throwable, Error]
  def authenticate(auth: AuthToken): Option[User]
}

class ConnectedClient[Channel, Event, Error, AuthToken, User](
    messages: Messages[Channel, Event, Error, AuthToken],
    handler: RequestHandler[Channel, Event, Error, AuthToken, User],
    dispatcher: Dispatcher[Channel, Event]
  ) extends Actor {

  import ConnectedClient._
  import messages._, handler._

  def connected(outgoing: ActorRef, user: Option[User] = None): Receive = {
    case Ping() => outgoing ! Pong()
    case CallRequest(seqId, path, args) =>
      val timer = StopWatch.started
      router(user).lift(Request(path, args))
        .map(_.map(resp => CallResponse(seqId, Right(resp))))
        .getOrElse(Future.successful(CallResponse(seqId, Left(pathNotFound(path)))))
        .recover(toError andThen { case err => CallResponse(seqId, Left(err)) })
        .||>(_.onComplete { _ => scribe.info(f"CallRequest($seqId): ${timer.readMicros}us") })
        .pipeTo(outgoing)
    case ControlRequest(seqId, control) =>
      val response = control match {
        case Login(token) =>
          val user = authenticate(token)
          context.become(connected(outgoing, user))
          user.isDefined
        case Logout() =>
          context.become(connected(outgoing))
          true
        case Subscribe(channel) =>
          dispatcher.subscribe(outgoing, channel)
          true
        case Unsubscribe(channel) =>
          dispatcher.unsubscribe(outgoing, channel)
          true
      }

      outgoing ! ControlResponse(seqId, response)
    case Stop =>
      dispatcher.unsubscribe(outgoing)
      context.stop(self)
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
