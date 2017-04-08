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
import wust.backend.auth.JWT

case class UserError(error: ApiError) extends Exception

class ApiRequestHandler extends RequestHandler[Channel, ApiEvent, ApiError, Authentication.Token, Authentication] {
  override def router(user: () => Future[Authentication]) =
    AutowireServer.route[Api](new ApiImpl(user)) orElse AutowireServer.route[AuthApi](new AuthApiImpl)

  override def createImplicitAuth: Future[Authentication] = Db.user.createImplicitUser().map(JWT.generateAuthentication)

  override def pathNotFound(path: Seq[String]): ApiError = NotFound(path)
  override val toError: PartialFunction[Throwable, ApiError] = {
    case UserError(error) => error
    case NonFatal(e) =>
      scribe.error("request handler threw exception", e)
      InternalServerError
  }

  override def authenticate(token: Authentication.Token): Future[Option[Authentication]] =
    JWT.authenticationFromToken(token)
      .map(auth => Db.user.check(auth.user).map(s => Option(auth).filter(_ => s)))
      .getOrElse(Future.successful(None))
}

object Server {
  private val ws = new WebsocketServer[Channel, ApiEvent, ApiError, Authentication.Token, Authentication](new ApiRequestHandler)

  private val route = (path("ws") & get) {
    ws.websocketHandler
  }

  def emit(event: ApiEvent) = ws.emit(Channel.fromEvent(event), event)
  def run(port: Int) = ws.run(route, "0.0.0.0", port)
}
