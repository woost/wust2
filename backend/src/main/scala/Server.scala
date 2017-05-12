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

class ApiRequestHandler(dispatcher: EventDispatcher, db: Db, enableImplicit: Boolean) extends RequestHandler[ApiEvent, ApiError, State] {
  private def stateChangeEvents(prevState: State, state: State): Seq[Future[ApiEvent]] =
    prevState.auth == state.auth match {
      case true => Seq.empty
      case false =>
        (
          state.auth
          .filter(_.user.isImplicit)
          .map(auth => ImplicitLogin(auth.toAuthentication))
          .toSeq
        ).map(Future.successful _) ++ Seq (
          db.graph.getAllVisiblePosts(state.user.map(_.id))
            .map(forClient(_).consistent)
            .map(ReplaceGraph(_))
        )
    }

  private def stateChangeResult(state: Future[State], newState: Future[State]) = for {
    state <- state
    newState <- newState
  } yield {
    val events = stateChangeEvents(state, newState)
    StateEvent(newState, events)
  }

  private def createImplicitUser() = enableImplicit match {
    case true => db.user.createImplicitUser().map(forClient(_) |> Option.apply)
    case false => Future.successful(None)
  }

  override def router(state: Future[State]) = {
    val validState = StateTranslator.filterValid(state)
    val stateAccess = new StateAccess(validState, createImplicitUser _, dispatcher.publish _)

    (
      AutowireServer.route[Api](new ApiImpl(stateAccess, db)) orElse
      AutowireServer.route[AuthApi](new AuthApiImpl(stateAccess, db))) andThen { res =>
        val newState = stateAccess.state
        RequestResult(stateChangeResult(state, newState), res)
      }
  }

  override def onEvent(event: ApiEvent, state: Future[State]) = {
    val validState = StateTranslator.filterValid(state)
    val newState = validState.map(StateTranslator.applyEvent(_, event))
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
  private val ws = new WebsocketServer[ApiEvent, ApiError, State](
    new ApiRequestHandler(new ChannelEventBus, Db.default, Config.auth.enableImplicit))

  private val route = (path("ws") & get) {
    ws.websocketHandler
  } ~ (path("health") & get) {
    complete("ok")
  }

  def run(port: Int) = ws.run(route, "0.0.0.0", port)
}
