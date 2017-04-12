package wust.backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import java.nio.ByteBuffer

import akka.http.scaladsl.server.Directives._
import akka.actor.ActorRef
import boopickle.Default._

import wust.api._
import wust.util.Pipe
import wust.framework._, message._
import wust.backend.auth._
import java.io.{StringWriter, PrintWriter}

case class UserError(error: ApiError) extends Exception

class ApiRequestHandler extends RequestHandler[Channel, ApiEvent, ApiError, Authentication.Token, Authentication] {

  private val enableImplicitAuth: Boolean = true //TODO config

  private def createImplicitAuth(): Future[Option[Authentication]] = {
    if (enableImplicitAuth) Db.user.createImplicitUser().map(JWT.generateAuthentication).map(Option.apply)
    else Future.successful(None)
  }

  override def router(currentAuth: Future[Option[Authentication]]) = {
    val apiAuth = new ApiAuthentication(currentAuth, createImplicitAuth _, UserError(Unauthorized))

    (AutowireServer.route[Api](new ApiImpl(apiAuth)) orElse
      AutowireServer.route[AuthApi](new AuthApiImpl(apiAuth))
      ) andThen {
        case res => (apiAuth.createdOrActualAuth, res)
      }
  }

  override def pathNotFound(path: Seq[String]): ApiError = NotFound(path)
  override val toError: PartialFunction[Throwable, ApiError] = {
    case UserError(error) => error
    case NonFatal(e) =>
      val sw = new StringWriter
      e.printStackTrace(new PrintWriter(sw))
      scribe.error("request handler threw exception:\n" + sw.toString)
      InternalServerError
  }

  override def authenticate(token: Authentication.Token): Future[Option[Authentication]] =
    JWT.authenticationFromToken(token)
      .map(auth => Db.user.check(auth.user).map(s => Option(auth).filter(_ => s)))
      .getOrElse(Future.successful(None))

  override def onLogin(auth: Authentication) = {
    import auth.user.{id => userId}
    Db.graph.get(Option(userId)).map(ReplaceGraph(_)).foreach(Server.emit)
    Db.user.allGroups(userId).map(ReplaceUserGroups(_)).foreach(Server.emit)
  }
}

object Server {
  private val ws = new WebsocketServer[Channel, ApiEvent, ApiError, Authentication.Token, Authentication](new ApiRequestHandler)

  private val route = (path("ws") & get) {
    ws.websocketHandler
  } ~ (path("health") & get) {
    complete("ok")
  }

  def emit(event: ApiEvent) = ws.emit(Channel.fromEvent(event), event)
  def run(port: Int) = ws.run(route, "0.0.0.0", port)
}
