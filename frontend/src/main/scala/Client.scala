package wust.frontend

import autowire._
import boopickle.Default._
import org.scalajs.dom.ext.LocalStorage
import wust.api._
import wust.framework._
import wust.util.Pipe

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class ApiException(error: ApiError) extends Exception

class ApiIncidentHandler extends IncidentHandler[ApiError] {
  override def fromError(error: ApiError) = ApiException(error)
}

object Client {
  private val handler = new ApiIncidentHandler
  private val storage = new ClientStorage(LocalStorage)
  val ws = new WebsocketClient[ApiEvent, ApiError](handler)

  val api = ws.wire[Api]
  val auth = new AuthClient(ws, storage)
  val onConnect = ws.onConnect _
  val onEvent = ws.onEvent _
  val run = ws.run _
}

trait AuthEvent
case class LoggedIn(user: User) extends AuthEvent
case object LoggedOut extends AuthEvent

class AuthClient(ws: WebsocketClient[ApiEvent, ApiError], storage: ClientStorage) {
  private val authApi = ws.wire[AuthApi]

  private var currentAuth: Future[Option[Authentication]] = Future.successful(None)
  private def storeToken(auth: Future[Option[Authentication]]) {
    currentAuth = auth
    currentAuth.foreach { auth =>
      storage.token = auth.map(_.token)
      sendAuthEvent(auth)
    }
  }

  private def sendAuthEvent(auth: Option[Authentication]): Unit =
    auth.map(_.user |> LoggedIn).getOrElse(LoggedOut) |> (x => eventHandler.foreach(_(x)))

  private def acknowledgeNewAuth(auth: Future[Option[Authentication]]): Unit = {
    val previousAuth = currentAuth
    val newAuth = auth.flatMap {
      case Some(auth) => Future.successful(Option(auth))
      case None => previousAuth.map(_.filter(_.user.isImplicit))
    }

    storeToken(newAuth)
  }

  private def loginFlow(auth: Future[Option[Authentication]]): Future[Boolean] =
    auth ||> acknowledgeNewAuth |> (_.map(_.isDefined))

  private var eventHandler: Option[AuthEvent => Any] = None
  def onEvent(handler: AuthEvent => Any): Unit = eventHandler = Option(handler)

  def register(name: String, pw: String): Future[Boolean] =
    authApi.register(name, pw).call() |> loginFlow

  def login(name: String, pw: String): Future[Boolean] =
    authApi.login(name, pw).call() |> loginFlow

  def logout(): Future[Boolean] =
    currentAuth.flatMap { auth =>
      auth
        //do not logout implicit users
        .filterNot(_.user.isImplicit)
        .map(_ => authApi.logout().call())
        .||>(_.foreach(_.filter(identity).map(_ => None) |> acknowledgeNewAuth))
        .getOrElse(Future.successful(false))
    }

  def reauthenticate(): Future[Boolean] =
    currentAuth.flatMap{ auth =>
      auth
        .map(_.token)
        .orElse(storage.token)
        .map(authApi.loginToken(_).call())
        .getOrElse(Future.successful(None))
    } |> loginFlow

  def acknowledgeAuth(auth: Authentication): Unit =
    acknowledgeNewAuth(Future.successful(Option(auth)))
}
