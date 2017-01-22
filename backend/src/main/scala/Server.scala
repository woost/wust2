package backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

import akka.http.scaladsl.server.Directives._
import boopickle.Default._
import com.outr.scribe._

import api._, framework._

case class User(name: String)

object TypePicklers {
  implicit val channelPickler = implicitly[Pickler[Channel]]
  implicit val eventPickler = implicitly[Pickler[ApiEvent]]
  implicit val authPickler = implicitly[Pickler[Authorize]]
  implicit val errorPickler = implicitly[Pickler[ApiError]]
}
import TypePicklers._

case class UserError(error: ApiError) extends Exception

object Server extends WebsocketServer[Channel, ApiEvent, ApiError, Authorize, User] with App {
  def router(user: Option[User]) = wire.route[Api](new ApiImpl(user, emit))

  def toError: PartialFunction[Throwable, ApiError] = {
    case UserError(error) => error
    case PathNotFoundException(path) => NotFound(path)
    case NonFatal(e) =>
      logger.error(s"request handler threw exception: ${e.getStackTrace}")
      InternalServerError
  }

  def authorize(auth: Authorize): Future[Option[User]] = auth match {
    case PasswordAuth(name, pw) =>
      val user = Model.users.find(u => u.name == name)
      Future.successful(user)
  }

  def emit(event: ApiEvent): Unit = emit(Channel.fromEvent(event), event)

  val route = pathSingleSlash {
      getFromResource("index-dev.html")
    } ~ pathPrefix("assets") {
      getFromResourceDirectory("public")
    }

  run("0.0.0.0", 8080) foreach { binding =>
    logger.info(s"Server online at ${binding.localAddress}")
  }
}
