package wust.backend

import java.io.{ PrintWriter, StringWriter }

import akka.http.scaladsl.server.Directives._
import boopickle.Default._
import derive.derive
import wust.api._
import wust.backend.auth._
import wust.framework._
import wust.util.Pipe
import wust.ids._
import wust.db.Db
import wust.backend.config.Config
import wust.backend.DbConversions._
import wust.graph.Group

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }
import collection.breakOut

// TODO: crashes coverage @derive(copyF)
case class State(auth: Option[JWTAuthentication], groupIds: Set[GroupId]) {
  val user = auth.map(_.user)
  def copyF(auth: Option[JWTAuthentication] => Option[JWTAuthentication] = identity, groupIds: Set[GroupId] => Set[GroupId] = identity) = copy(auth = auth(this.auth), groupIds = groupIds(this.groupIds))
}
object State {
  def initial = State(auth = None, groupIds = Set.empty)
}

class ApiRequestHandler(dispatcher: EventDispatcher, enableImplicit: Boolean) extends RequestHandler[ApiEvent, ApiError, State] {
  private def onStateChange(prevState: State, state: State): Seq[Future[ApiEvent]] = {
    if (prevState.auth != state.auth)
      state.auth
        .filter(_.user.isImplicit)
        .map(auth => ImplicitLogin(auth.toAuthentication))
        .map(Future.successful _)
        .toSeq ++ Seq (
          Db.graph.getAllVisiblePosts(state.user.map(_.id))
            .map(forClient(_).consistent)
            .map(ReplaceGraph(_))
        )
    else Seq.empty
  }

  private def stateChangeResult(state: Future[State], newState: Future[State]) = for {
    state <- state
    newState <- newState
  } yield {
    val events = onStateChange(state, newState)
    StateEvent(newState, events)
  }

  private def validatedState(state: Future[State]): Future[State] = state.map(_.copyF(auth = _.filterNot(JWT.isExpired)))

  private def createImplicitUser() = enableImplicit match {
    case true => Db.user.createImplicitUser().map(forClient(_) |> Option.apply)
    case false => Future.successful(None)
  }

  override def router(state: Future[State]) = {
    val stateAccess = new StateAccess(validatedState(state), createImplicitUser _, dispatcher.publish _)

    (
      AutowireServer.route[Api](new ApiImpl(stateAccess)) orElse
      AutowireServer.route[AuthApi](new AuthApiImpl(stateAccess))) andThen { res =>
        val newState = stateAccess.state
        RequestResult(stateChangeResult(state, newState), res)
      }
  }

  override def onEvent(event: ApiEvent, state: Future[State]) = {
    val newState = validatedState(state).map(StateTranslator.applyEvent(_, event))
    stateChangeResult(state, newState)
  }

  override def onClientStart(sender: EventSender[ApiEvent]) = {
    val state = State.initial
    scribe.info(s"client started: $state")
    dispatcher.subscribe(sender, Channel.All)
    Future.successful(state)
  }

  override def onClientStop(sender: EventSender[ApiEvent], state: State) = {
    scribe.info(s"client stopped: $state")
    dispatcher.unsubscribe(sender)
  }

  override def pathNotFound(path: Seq[String]): ApiError = NotFound(path)
  override val toError: PartialFunction[Throwable, ApiError] = {
    case ApiException(error) => error
    case NonFatal(e) =>
      scribe.error("request handler threw exception")
      scribe.error(e)
      InternalServerError
  }
}

object Server {
  private val dispatcher = new ChannelEventBus
  private val ws = new WebsocketServer[ApiEvent, ApiError, State](new ApiRequestHandler(dispatcher, Config.auth.enableImplicit))

  private val route = (path("ws") & get) {
    ws.websocketHandler
  } ~ (path("health") & get) {
    complete("ok")
  }

  def run(port: Int) = ws.run(route, "0.0.0.0", port)
}
