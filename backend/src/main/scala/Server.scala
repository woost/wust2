package backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

import akka.http.scaladsl.server.Directives._
import boopickle.Default._
import com.outr.scribe._

import api._, framework._

object TypePicklers {
  implicit val channelPickler = implicitly[Pickler[Channel]]
  implicit val eventPickler = implicitly[Pickler[ApiEvent]]
  implicit val authPickler = implicitly[Pickler[Authorize]]
  implicit val errorPickler = implicitly[Pickler[ApiError]]
}
import TypePicklers._

case class UserError(error: ApiError) extends Exception

object Server extends WebsocketServer[Channel, ApiEvent, ApiError, Authorize, User] with App {
  override def router(user: Option[User]) =
    wire.route[Api](new ApiImpl(user, emit)) orElse wire.route[AuthApi](new AuthApiImpl)

  override def pathNotFound(path: Seq[String]): ApiError = NotFound(path)
  override def toError: PartialFunction[Throwable, ApiError] = {
    case UserError(error) => error
    case NonFatal(e) =>
      logger.error("request handler threw exception", e)
      InternalServerError
  }

  override def authorize(auth: Authorize): Future[Option[User]] = auth match {
    case PasswordAuth(name, pw) => Db.user.get(name, pw).map(_.headOption)
  }

  override val route = (path("ws") & get) {
    websocketHandler
  }

  def emit(event: ApiEvent): Unit = emit(Channel.fromEvent(event), event)

  run("0.0.0.0", 8080) foreach { binding =>
    logger.info(s"Server online at ${binding.localAddress}")
  }
}
