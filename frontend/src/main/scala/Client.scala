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

case class ConnectEvent(location: String)

class ApiIncidentHandler extends IncidentHandler[ApiEvent, ApiError] with WithEvents[Either[ConnectEvent, ApiEvent]] {
  override def fromError(error: ApiError) = ApiException(error)
  override def onConnect(location: String) = sendEvent(Left(ConnectEvent(location)))
  override def receive(event: ApiEvent) = sendEvent(Right(event))
}

object Client {
  private val handler = new ApiIncidentHandler
  private val ws = new WebsocketClient[Channel, ApiEvent, ApiError, Authentication.Token](handler)
  private val storage = new ClientStorage(LocalStorage)

  val api = ws.wire[Api]
  val auth = new AuthClient(ws, storage)
  val listen = handler.listen _
  val subscribe = ws.subscribe _
  val unsubscribe = ws.unsubscribe _
  val run = ws.run _
}

trait AuthEvent
case class LoggedIn(user: User) extends AuthEvent
case object LoggedOut extends AuthEvent

class AuthClient(
    ws:      WebsocketClient[Channel, ApiEvent, ApiError, Authentication.Token],
    storage: ClientStorage) extends WithEvents[AuthEvent] {
  import ws.messages._

  private val authApi = ws.wire[AuthApi]

  private var currentAuth: Future[Option[Authentication]] =
    Future.successful(storage.getAuth)

  private def storeToken(auth: Future[Option[Authentication]]) {
    auth.foreach(storage.setAuth)
    currentAuth = auth
  }

  private def sendAuthEvent(auth: Future[Option[Authentication]]): Unit =
    auth.foreach(_.map(_.user |> LoggedIn).getOrElse(LoggedOut) |> sendEvent)

  private def withClientLogin(auth: Future[Option[Authentication]]): Future[Option[Authentication]] =
    auth.flatMap(_.map(auth => ws.login(auth.token).map(if (_) Some(auth) else None)).getOrElse(Future.successful(None)))

  private def loginFlow(auth: Future[Option[Authentication]]): Future[Boolean] =
    auth |> withClientLogin ||> storeToken ||> sendAuthEvent |> (_.map(_.isDefined))

  def reauthenticate(): Future[Boolean] =
    currentAuth |> loginFlow

  def register(name: String, pw: String): Future[Boolean] =
    authApi.register(name, pw).call() |> loginFlow

  def login(name: String, pw: String): Future[Boolean] =
    authApi.login(name, pw).call() |> loginFlow

  def logout(): Future[Boolean] =
    ws.logout() ||> (_ => (Future.successful(None) ||> storeToken ||> sendAuthEvent))
}
