package wust.backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import java.nio.ByteBuffer

import akka.http.scaladsl.server.Directives._
import akka.actor.ActorRef
import boopickle.Default._

import wust.api._
import wust.framework._, message._
import auth.JWTOps

case class UserError(error: ApiError) extends Exception

class ApiRequestHandler(
    messages: Messages[Channel, ApiEvent, ApiError, JWT.Token],
    wire: autowire.Server[ByteBuffer, Pickler, Pickler]) extends RequestHandler[Channel, ApiEvent, ApiError, JWT.Token, Authentication] {

  def emit(event: ApiEvent): Unit = dispatcher.emit(Channel.fromEvent(event), event)

  override val dispatcher = new EventDispatcher(messages)

  override def router(user: Option[Authentication]) =
    wire.route[Api](new ApiImpl(user, emit)) orElse wire.route[AuthApi](new AuthApiImpl)

  override def pathNotFound(path: Seq[String]): ApiError = NotFound(path)
  override val toError: PartialFunction[Throwable, ApiError] = {
    case UserError(error) => error
    case NonFatal(e) =>
      scribe.error("request handler threw exception", e)
      InternalServerError
  }

  override def authenticate(token: JWT.Token): Option[Authentication] =
    Some(token).filter(JWTOps.isValid).map { token =>
      val user = JWTOps.userFromToken(token)
      Authentication(user, token)
    }
}

object Server {
  private val wire = AutowireServer
  private val messages = new Messages[Channel, ApiEvent, ApiError, JWT.Token]
  private val handler = new ApiRequestHandler(messages, wire)
  private val ws = new WebsocketServer(messages, handler)

  private val route = (path("ws") & get) {
    ws.websocketHandler
  }

  def run(port: Int) = ws.run(route, "0.0.0.0", port)
}
