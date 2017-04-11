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
  def router(currentAuth: ConnectionAuth[Auth]): PartialFunction[Request[ByteBuffer], Future[ByteBuffer]]
  def pathNotFound(path: Seq[String]): Error
  def toError: PartialFunction[Throwable, Error]
  def implicitAuth(): Future[Option[Auth]]
  def authenticate(currentAuth: AuthToken): Future[Option[Auth]]
  def onLogin(authOpt: Auth): Any = {}
}

class ConnectionAuth[Auth] private (actualAuth: Future[Option[Auth]])(newImplicitAuth: () => Future[Option[Auth]]) {
  import scala.concurrent.Promise

  private lazy val implicitAuth = newImplicitAuth()
  private val implicitAuthPromise = Promise[Future[Option[Auth]]]()
  private val implicitAuthFuture = implicitAuthPromise.future.flatMap(f => f)

  def auth: Future[Option[Auth]] = actualAuth.flatMap {
    case Some(auth) => Future.successful(Option(auth))
    case None =>
      if (implicitAuthPromise.isCompleted) implicitAuthFuture
      else Future.successful(None)
  }

  def authOrImplicit: Future[Option[Auth]] = actualAuth.flatMap {
    case Some(auth) => Future.successful(Option(auth))
    case None =>
      implicitAuthPromise trySuccess implicitAuth
      implicitAuthFuture
  }
}
object ConnectionAuth {
  def unauthenticated[Auth](implicitAuth: () => Future[Option[Auth]]) = new ConnectionAuth[Auth](Future.successful(None))(implicitAuth)
  def authenticated[Auth](auth: Future[Option[Auth]]) = new ConnectionAuth[Auth](auth)(() => auth)
}

class ConnectedClient[Channel, Event, Error, AuthToken, Auth](
  messages: Messages[Channel, Event, Error, AuthToken, Auth],
  handler: RequestHandler[Channel, Event, Error, AuthToken, Auth],
  dispatcher: Dispatcher[Channel, Event]
) extends Actor {

  import ConnectedClient._
  import messages._, handler._

  def connected(outgoing: ActorRef, currentAuthOpt: Option[ConnectionAuth[Auth]] = None): Receive = {
    def sendImplicitAuth(auth: Auth) = outgoing ! ControlNotification(ImplicitLogin(auth))
    val currentAuth = currentAuthOpt.getOrElse {
      ConnectionAuth.unauthenticated[Auth](() =>
        handler.implicitAuth ||> (_.foreach(_.foreach(sendImplicitAuth))))
    }

    {
      case Ping() => outgoing ! Pong()
      case CallRequest(seqId, path, args) =>
        val timer = StopWatch.started
        router(currentAuth).lift(Request(path, args))
          .map(_.map(resp => CallResponse(seqId, Right(resp))))
          .getOrElse(Future.successful(CallResponse(seqId, Left(pathNotFound(path)))))
          .recover(toError andThen { case err => CallResponse(seqId, Left(err)) })
          .||>(_.onComplete { _ => scribe.info(f"CallRequest($seqId): ${timer.readMicros}us") })
          .pipeTo(outgoing)
      case ControlRequest(seqId, control) => control match {
        case Login(token) =>
          val auth = authenticate(token)
          context.become(connected(outgoing, Some(ConnectionAuth.authenticated(auth))))
          auth
            .map(auth => ControlResponse(seqId, auth.isDefined))
            .pipeTo(outgoing)

          auth
            .foreach(_.foreach(onLogin))
        case Logout() =>
          context.become(connected(outgoing))
          outgoing ! ControlResponse(seqId, true)
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
