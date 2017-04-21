package wust.frontend

import concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import org.scalajs.dom.ext.LocalStorage
import boopickle.Default._
import autowire._

import wust.framework._, message._
import wust.api._
import wust.graph._
import wust.util.Pipe

case class ApiException(error: ApiError) extends Exception

sealed trait IncidentEvent
case class ConnectEvent(location: String) extends IncidentEvent
case class ConnectionEvent(event: ApiEvent) extends IncidentEvent

class ApiIncidentHandler extends IncidentHandler[ApiError] {
  override def fromError(error: ApiError) = ApiException(error)
}

object Client {
  private val handler = new ApiIncidentHandler
  private val storage = new ClientStorage(LocalStorage)
  val ws = new WebsocketClient[ApiEvent, ApiError](handler)

  val api = ws.wire[Api]
  val auth = new AuthClient(ws, storage, id => api.getUser(id).call())
  val onConnect = ws.onConnect _
  val onEvent = ws.onEvent _
  val run = ws.run _
}

trait AuthEvent
case class LoggedIn(user: User) extends AuthEvent
case object LoggedOut extends AuthEvent

class AuthClient(
  ws: WebsocketClient[ApiEvent, ApiError],
  storage: ClientStorage,
  getUser: Long => Future[Option[User]]
) {
  private val authApi = ws.wire[AuthApi]

  private var currentAuth: Future[Option[Authentication]] = Future.successful(None)
  private def storedToken: Option[Authentication.Token] = storage.token
  private def storeToken(auth: Future[Option[Authentication]]) {
    currentAuth = auth
    currentAuth.foreach(auth => storage.token = auth.map(_.token))
  }

  private def sendAuthEvent(auth: Future[Option[Authentication]]): Unit =
    auth.foreach(_.map(_.user |> LoggedIn).getOrElse(LoggedOut) |> (x => eventHandler.foreach(_(x))))

  private def acknowledgeNewAuth(auth: Future[Option[Authentication]]): Unit = {
    val previousAuth = currentAuth
    auth.flatMap {
      case Some(auth) => Future.successful(Option(auth))
      case None => previousAuth.map(_.filter(_.user.isImplicit))
    } ||> storeToken ||> sendAuthEvent
  }

  private def loginFlow(auth: Future[Option[Authentication]]): Future[Boolean] =
    auth ||> acknowledgeNewAuth |> (_.map(_.isDefined))

  private var eventHandler: Option[AuthEvent => Any] = None
  def onEvent(handler: AuthEvent => Any): Unit = eventHandler = Option(handler)

  def reauthenticate(): Future[Boolean] =
    currentAuth.flatMap(authOpt => authOpt.map(_.token).orElse(storedToken).map(token => authApi.loginToken(token).call()).getOrElse(Future.successful(None))) |> loginFlow

  def register(name: String, pw: String): Future[Boolean] =
    authApi.register(name, pw).call() |> loginFlow

  def login(name: String, pw: String): Future[Boolean] =
    authApi.login(name, pw).call() |> loginFlow

  def logout(): Future[Boolean] =
    currentAuth.flatMap(_.filterNot(_.user.isImplicit).map(_ => authApi.logout().call() ||> (_ => (Future.successful(None) ||> acknowledgeNewAuth))).getOrElse(Future.successful(false)))

  def acknowledgeAuth(auth: Authentication): Unit =
    acknowledgeNewAuth(Future.successful(Option(auth)))
}
