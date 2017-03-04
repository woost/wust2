package framework

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import java.nio.ByteBuffer

import akka.actor._
import akka.pattern.pipe
import autowire.Core.{Request, Router}

import framework.message._
import util.time.StopWatch
import util.Pipe

//TODO decouple. channel dependency, then subscribe, signal => action!
class ConnectedClient[CHANNEL, AUTH, ERROR, USER](
  messages: Messages[CHANNEL, _, ERROR, AUTH],
  dispatcher: Dispatcher[CHANNEL, _],
  router: Option[USER] => Router[ByteBuffer],
  pathNotFound: Seq[String] => ERROR,
  toError: PartialFunction[Throwable, ERROR],
  authorize: AUTH => Future[Option[USER]]
) extends Actor {
  import messages._, ConnectedClient._

  val notAuthenticated: Future[Option[USER]] = Future.successful(None)

  def connected(outgoing: ActorRef, user: Future[Option[USER]]): Receive = {
    case Ping() => outgoing ! Pong()
    case CallRequest(seqId, path, args) =>
      val timer = StopWatch.started

      router(user.value.flatMap(_.toOption.flatten)).lift(Request(path, args))
        .map(_.map(resp => CallResponse(seqId, Right(resp))))
        .getOrElse(Future.successful(CallResponse(seqId, Left(pathNotFound(path)))))
        .recover(toError andThen { case err => CallResponse(seqId, Left(err)) })
        .||>(_.onComplete { _ => scribe.info(f"CallRequest($seqId): ${timer.readMicros}us") })
        .pipeTo(outgoing)
    case ControlRequest(seqId, control) => control match {
      case Login(auth) =>
        val timer = StopWatch.started

        val nextUser = authorize(auth)
        nextUser.map(_.isDefined)
          .recover { case NonFatal(_) => false }
          .map(ControlResponse(seqId, _))
          .||>(_.onComplete { _ => scribe.info(f"Login($seqId): ${timer.readMicros}us") })
          .pipeTo(outgoing)

        context.become(connected(outgoing, nextUser))
      case Logout() =>
        outgoing ! ControlResponse(seqId, true)
        context.become(connected(outgoing, notAuthenticated))
    }
    case Subscription(channel) => dispatcher.subscribe(outgoing, channel)
    case Stop =>
      dispatcher.unsubscribe(outgoing)
      context.stop(self)
  }

  def receive = {
    case Connect(outgoing) => context.become(connected(outgoing, notAuthenticated))
    case Stop => context.stop(self)
  }
}
object ConnectedClient {
  case class Connect(actor: ActorRef)
  case object Stop
}
