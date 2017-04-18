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

case class State(auth: Option[JWTAuthentication])

class ApiRequestHandler extends RequestHandler[ApiEvent, ApiError, Authentication.Token, State] {

  private val enableImplicitAuth: Boolean = true //TODO config

  private def createImplicitAuth(): Future[Option[JWTAuthentication]] = {
    if (enableImplicitAuth) Db.user.createImplicitUser().map(JWT.generateAuthentication).map(Option.apply)
    else Future.successful(None)
  }

  override def router(state: Future[State]) = {
    val apiAuth = new AuthenticatedAccess(state.map(_.auth), createImplicitAuth _, UserError(Unauthorized))

    (AutowireServer.route[Api](new ApiImpl(apiAuth)) orElse
      AutowireServer.route[AuthApi](new AuthApiImpl(apiAuth))) andThen {
        case res =>
          val newState = for {
            state <- state
            auth <- apiAuth.createdOrActualAuth
          } yield state.copy(auth = auth)

          (newState, res)
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

  override val initialState = Future.successful(State(None))
  override def authenticate(state: Future[State], token: Authentication.Token): Future[Option[State]] =
    JWT.authenticationFromToken(token).map { auth =>
      for {
        valid <- Db.user.checkEqualUserExists(auth.user)
        state <- state
      } yield {
        if (valid) Option(state.copy(auth = Option(auth)))
        else None
      }
    }.getOrElse(Future.successful(None))

  override def onStateChange(state: State) = state.auth.toSeq.flatMap { auth =>
    import auth.user.{id => userId}
    val loginEvent = if (auth.user.isImplicit) Option(ImplicitLogin(auth.toAuthentication)) else None

    loginEvent.toSeq.map(Future.successful) ++ Seq(
      Db.graph.getAllVisiblePosts(Option(userId)).map(ReplaceGraph(_)),
      Db.user.allGroups(userId).map(ReplaceUserGroups(_))
    )
  }
}

object Server {
  private val ws = new WebsocketServer[Channel, ApiEvent, ApiError, Authentication.Token, State](new ApiRequestHandler)

  private val route = (path("ws") & get) {
    ws.websocketHandler
  } ~ (path("health") & get) {
    complete("ok")
  }

  //TODO: this is weird, we are actually only emitting if there is a corresponding channel
  // because some apievent are not meant as channel-events but only used for the currently logged in user
  // idea: have an apievent for channel user(id) instead?
  // this would be consistent with the other eventhandling
  def emit(event: ApiEvent) = Channel.fromEvent.lift(event).foreach(ws.emit(_, event))

  def run(port: Int) = ws.run(route, "0.0.0.0", port)
}
