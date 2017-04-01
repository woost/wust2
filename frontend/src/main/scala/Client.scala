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

class ApiIncidentHandler extends IncidentHandler[ApiEvent, ApiError] {
  override def fromError(error: ApiError) = ApiException(error)
}

object Client {
  private val handler = new ApiIncidentHandler
  private val storage = new ClientStorage(LocalStorage)
  val ws = new WebsocketClient[Channel, ApiEvent, ApiError, Authentication.Token, Authentication](handler)

  val api = ws.wire[Api]
  val auth = new AuthClient(ws, storage)
  val onConnect = ws.onConnect _
  val onEvent = ws.onEvent _
  val subscribe = ws.subscribe _
  val unsubscribe = ws.unsubscribe _
  val run = ws.run _
}

trait AuthEvent
case class LoggedIn(user: User) extends AuthEvent
case object LoggedOut extends AuthEvent

class AuthClient(
    ws:      WebsocketClient[Channel, ApiEvent, ApiError, Authentication.Token, Authentication],
    storage: ClientStorage) {
  import ws.messages._

  ws.onControlEvent {
    case ImplicitLogin(auth) =>
      Future.successful(Some(auth)) ||> storeToken ||> sendAuthEvent
  }

  private val authApi = ws.wire[AuthApi]

  private var currentAuth: Future[Option[Authentication]] =
    Future.successful(storage.getAuth)

  private def storeToken(auth: Future[Option[Authentication]]) {
    auth.foreach(storage.setAuth)
    currentAuth = auth
  }

  private def sendAuthEvent(auth: Future[Option[Authentication]]): Unit =
    auth.foreach(_.map(_.user |> LoggedIn).getOrElse(LoggedOut) |> (x => eventHandler.foreach(_(x))))

  private def withClientLogin(auth: Future[Option[Authentication]]): Future[Option[Authentication]] =
    auth.flatMap(_.map(auth => ws.login(auth.token).map(if (_) Some(auth) else None)).getOrElse(Future.successful(None)))

  private def loginFlow(auth: Future[Option[Authentication]]): Future[Boolean] =
    auth |> withClientLogin ||> storeToken ||> sendAuthEvent |> (_.map(_.isDefined))

  private var eventHandler: Option[AuthEvent => Any] = None
  def onEvent(handler: AuthEvent => Any): Unit = eventHandler = Some(handler)

  def reauthenticate(): Future[Boolean] =
    currentAuth |> loginFlow

  def register(name: String, pw: String): Future[Boolean] = currentAuth.map(_.filter(_.user.isImplicit)).flatMap {
    case Some(auth) => authApi.registerImplicit(name, pw, auth.token).call()
    case None => authApi.register(name, pw).call()
  } |> loginFlow

  def login(name: String, pw: String): Future[Boolean] =
    authApi.login(name, pw).call() |> loginFlow

  def logout(): Future[Boolean] =
    ws.logout() ||> (_ => (Future.successful(None) ||> storeToken ||> sendAuthEvent))
}
