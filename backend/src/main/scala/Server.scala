package wust.backend

import java.io.{ PrintWriter, StringWriter }

import akka.http.scaladsl.server.Directives._
import autowire.Core.Request
import java.nio.ByteBuffer
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

class ApiRequestHandler(dispatcher: EventDispatcher, stateChange: StateChange, api: StateAccess => PartialFunction[Request[ByteBuffer], Future[ByteBuffer]]) extends RequestHandler[ApiEvent, ApiError, State] {
  import StateTranslator._, stateChange._

  override def router(state: Future[State]) = {
    val validState = state.map(filterValid)
    val stateAccess = new StateAccess(validState, dispatcher.publish _, createImplicitAuth _)

    api(stateAccess) andThen { res =>
      val newState = stateAccess.state
      val events = for {
        state <- state
        newState <- newState
      } yield stateChangeEvents(state, newState)

      RequestResult(StateWithEvents(newState, events), res)
    }
  }

  override def onEvent(event: ApiEvent, state: Future[State]) = {
    val validState = state.map(filterValid)
    val newState = validState.map(applyEvent(_, event))
    val events = for {
      state <- state
      newState <- newState
    } yield stateChangeEvents(state, newState) ++ Seq(event).filter(allowsEvent(newState, _)).map(Future.successful)

    StateWithEvents(newState, events)
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
  private def api(stateAccess: StateAccess) = AutowireServer.route[Api](new ApiImpl(stateAccess, Db.default)) orElse
      AutowireServer.route[AuthApi](new AuthApiImpl(stateAccess, Db.default, JWT.default))

  private val stateChange = new StateChange(Db.default, JWT.default, Config.auth.enableImplicit)

  private val ws = new WebsocketServer[ApiEvent, ApiError, State](new ApiRequestHandler(new ChannelEventBus, stateChange, api _))

  private val route = (path("ws") & get) {
    ws.websocketHandler
  } ~ (path("health") & get) {
    complete("ok")
  }

  def run(port: Int) = ws.run(route, "0.0.0.0", port)
}
