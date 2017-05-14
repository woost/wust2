package wust.backend

import java.nio.ByteBuffer

import akka.http.scaladsl.server.Directives._
import autowire.Core.Request
import boopickle.Default._
import wust.api._
import wust.backend.auth._
import wust.config.Config
import wust.db.Db
import wust.framework._
import wust.framework.state.StateHolder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

class ApiRequestHandler(dispatcher: EventDispatcher, stateInterpreter: StateInterpreter, api: StateHolder[State, ApiEvent] => PartialFunction[Request[ByteBuffer], Future[ByteBuffer]]) extends RequestHandler[ApiEvent, ApiError, State] {
  import StateInterpreter._
  import stateInterpreter._

  override def before(state: Future[State]) = state.map(filterValid)
  override def after(state: Future[State], newState: Future[State]) = for {
    state <- state
    newState <- newState
  } yield stateChangeEvents(state, newState)

  override def router(holder: StateHolder[State, ApiEvent]) = api(holder)
  override def isEventAllowed(event: ApiEvent, state: Future[State]) = state.map(allowsEvent(_, event))
  override def publishEvent(event: ApiEvent) = dispatcher.publish(ChannelEvent(Channel.All, event))

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
  private val dsl = new GuardDsl(Db.default, Config.auth.enableImplicit)
  private val stateInterpreter = new StateInterpreter(Db.default)

  private def api(holder: StateHolder[State, ApiEvent]) = AutowireServer.route[Api](new ApiImpl(holder, dsl, Db.default)) orElse
      AutowireServer.route[AuthApi](new AuthApiImpl(holder, dsl, Db.default))

  private val ws = new WebsocketServer[ApiEvent, ApiError, State](new ApiRequestHandler(new ChannelEventBus, stateInterpreter, api _))

  private val route = (path("ws") & get) {
    ws.websocketHandler
  } ~ (path("health") & get) {
    complete("ok")
  }

  def run(port: Int) = ws.run(route, "0.0.0.0", port)
}
