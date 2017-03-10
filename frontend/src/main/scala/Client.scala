package wust.frontend

import concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import boopickle.Default._
import autowire._

import wust.framework._, message._
import wust.api._
import wust.graph._
import wust.util.Pipe

case class ApiException(error: ApiError) extends Exception

class ApiIncidentHandler(sendEvent: ApiEvent => Unit) extends IncidentHandler[ApiEvent, ApiError] {
  override def fromError(error: ApiError) = ApiException(error)
  override def receive(event: ApiEvent) = sendEvent(event)
}

object Client extends WithEvents[ApiEvent] {
  private val handler = new ApiIncidentHandler(sendEvent)
  private val ws = new WebsocketClient[Channel, ApiEvent, ApiError, Authentication.Token](handler)

  val api = ws.wire[Api]
  val auth = new AuthClient(ws)
  val subscribe = ws.subscribe _
  val unsubscribe = ws.unsubscribe _
  val run = ws.run _
}

trait AuthEvent
case class LoggedIn(user: User) extends AuthEvent
case object LoggedOut extends AuthEvent

class AuthClient(ws: WebsocketClient[Channel, ApiEvent, ApiError, Authentication.Token]) extends WithEvents[AuthEvent] {
  import ws.messages._

  private val auth = ws.wire[AuthApi]
  private var currentTokenFut: Future[Option[Authentication.Token]] = Future.successful(None)
  private def storeToken(auth: Future[Option[Authentication]]) = currentTokenFut = auth.map(_.map(_.token))
  private def currentToken = currentTokenFut.value.flatMap(_.toOption).flatten

  private def withClientLogin(auth: Future[Option[Authentication]]) =
    auth.flatMap(_.map (auth => ws.login(auth.token).map(if (_) Some(auth) else None)).getOrElse(Future.successful(None)))

  private def sendAuthEvent(auth: Future[Option[Authentication]]) =
    auth.foreach(_.map(_.user |> LoggedIn).getOrElse(LoggedOut) |> sendEvent)

  def reauthenticate(): Future[Boolean] =
    currentToken.map(ws.login).getOrElse(Future.successful(false))

  def register(name: String, pw: String): Future[Boolean] =
    auth.register(name, pw).call() ||> storeToken |> withClientLogin ||> sendAuthEvent |> (_.map(_.isDefined))

  def login(name: String, pw: String): Future[Boolean] =
    auth.login(name, pw).call() ||> storeToken |> withClientLogin ||> sendAuthEvent |> (_.map(_.isDefined))

  def logout(): Future[Boolean] =
    ws.logout() ||> (_ => (Future.successful(None) ||> storeToken ||> sendAuthEvent))
}
