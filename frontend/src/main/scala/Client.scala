package frontend

import concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import boopickle.Default._
import autowire._

import framework._, message._
import api._, graph._
import util.Pipe

case class ApiException(error: ApiError) extends Exception

class ApiIncidentHandler(sendEvent: ApiEvent => Unit) extends IncidentHandler[ApiEvent, ApiError] {
  override def fromError(error: ApiError) = ApiException(error)
  override def receive(event: ApiEvent) = sendEvent(event)
}

object Client extends WithEvents[ApiEvent] {
  private val wire = new AutowireClient(ws.call)
  private val messages = new Messages[Channel, ApiEvent, ApiError, JWT.Token]
  private val handler = new ApiIncidentHandler(sendEvent)
  private val ws = new WebsocketClient(messages, handler)

  val api = wire[Api]
  val auth = new AuthClient(wire, ws)

  import ws.messages._
  def subscribe(channel: Channel): Future[Boolean] = ws.control(Subscribe(channel))
  def unsubscribe(channel: Channel): Future[Boolean] = ws.control(Unsubscribe(channel))
  val run = ws.run _
}

trait AuthEvent
case class LoggedIn(user: User) extends AuthEvent
case object LoggedOut extends AuthEvent

class AuthClient(
    wire: autowire.Client[_, Pickler, Pickler],
    ws:   WebsocketClient[Channel, ApiEvent, ApiError, JWT.Token]) extends WithEvents[AuthEvent] {

  import ws.messages._

  private val auth = wire[AuthApi]
  private var currentTokenFut: Future[Option[JWT.Token]] = Future.successful(None)
  private def storeToken(auth: Future[Option[Authentication]]) = currentTokenFut = auth.map(_.map(_.token))
  private def currentToken = currentTokenFut.value.flatMap(_.toOption).flatten

  private def withClientLogin(auth: Future[Option[Authentication]]) =
    auth.flatMap(_.map { auth =>
      ws.control(Login(auth.token)).map(if (_) Some(auth) else None)
    }.getOrElse(Future.successful(None)))

  private def sendAuthEvent(auth: Future[Option[Authentication]]) =
    auth.foreach(_.map(_.user |> LoggedIn).getOrElse(LoggedOut) |> sendEvent)


  def reauthenticate(): Future[Boolean] =
    currentToken.map(token => ws.control(Login(token))).getOrElse(Future.successful(false))

  def register(name: String, pw: String): Future[Boolean] =
    auth.register(name, pw).call() ||> storeToken |> withClientLogin ||> sendAuthEvent |> (_.map(_.isDefined))

  def login(name: String, pw: String): Future[Boolean] =
    auth.login(name, pw).call() ||> storeToken |> withClientLogin ||> sendAuthEvent |> (_.map(_.isDefined))

  def logout(): Future[Boolean] =
    ws.control(Logout()) |> (_ => Future.successful(None)) ||> storeToken ||> sendAuthEvent |> (_.map(_.isDefined))
}
