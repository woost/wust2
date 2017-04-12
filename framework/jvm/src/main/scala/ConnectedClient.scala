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

trait RequestHandler[Channel, Event, Error, AuthToken, Auth] {
  def router(currentAuth: Future[Option[Auth]]): PartialFunction[Request[ByteBuffer], (Future[Option[Auth]], Future[ByteBuffer])]
  def pathNotFound(path: Seq[String]): Error
  def toError: PartialFunction[Throwable, Error]
  def authenticate(currentAuth: AuthToken): Future[Option[Auth]]
  def onLogin(authOpt: Auth): Any = {}
}

class ConnectedClient[Channel, Event, Error, AuthToken, Auth](
  messages: Messages[Channel, Event, Error, AuthToken, Auth],
  handler: RequestHandler[Channel, Event, Error, AuthToken, Auth],
  dispatcher: Dispatcher[Channel, Event]
) extends Actor {

  import ConnectedClient._
  import messages._, handler._

  def connected(outgoing: ActorRef, currentAuth: Future[Option[Auth]] = Future.successful(None)): Receive = {
      case Ping() => outgoing ! Pong()
      case CallRequest(seqId, path, args) =>
        router(currentAuth).lift(Request(path, args)) match {
          case Some((newAuth, response)) =>
            val timer = StopWatch.started
            response
              //TODO: transform = map + recover?
              .map(resp => CallResponse(seqId, Right(resp)))
              .recover(toError andThen { case err => CallResponse(seqId, Left(err)) })
              .||>(_.onComplete { _ => scribe.info(f"CallRequest($seqId): ${timer.readMicros}us") })
              .pipeTo(outgoing)

            for {
              currentAuth <- currentAuth
              newAuth <- newAuth
            } newAuth.filterNot(currentAuth.toSet).foreach { auth =>
              outgoing ! ControlNotification(ImplicitLogin(auth))
              onLogin(auth)
            }

            context.become(connected(outgoing, newAuth))
          case None =>
            outgoing ! CallResponse(seqId, Left(pathNotFound(path)))
        }
      case ControlRequest(seqId, control) => control match {
        case Login(token) =>
          val auth = authenticate(token)
          auth
            .map(auth => ControlResponse(seqId, auth.isDefined))
            .pipeTo(outgoing)

          auth.foreach(_.foreach(onLogin))
          context.become(connected(outgoing, auth))
        case Logout() =>
          outgoing ! ControlResponse(seqId, true)
          context.become(connected(outgoing))
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
    case Connect(outgoing) => context.become(connected(outgoing))
    case Stop => context.stop(self)
  }
}
object ConnectedClient {
  case class Connect(actor: ActorRef)
  case object Stop
}
