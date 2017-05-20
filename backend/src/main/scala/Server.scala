package wust.backend

import java.nio.ByteBuffer

import akka.http.scaladsl.server.Directives._
import autowire.Core.Request
import boopickle.Default._
import wust.api._
import wust.backend.auth._
import wust.backend.config.Config
import wust.db.Db
import wust.framework._
import wust.framework.state.StateHolder

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ApiRequestHandler(distributor: EventDistributor, stateInterpreter: StateInterpreter, api: StateHolder[State, ApiEvent] => PartialFunction[Request[ByteBuffer], Future[ByteBuffer]])(implicit ec: ExecutionContext) extends RequestHandler[ApiEvent, ApiError, State] {
  import stateInterpreter._

  override def initialState = State.initial

  override def validate(state: State) = stateInterpreter.validate(state)

  override def onRequest(holder: StateHolder[State, ApiEvent], request: Request[ByteBuffer]) = {
    val handler = api(holder).lift
    handler(request).toRight(NotFound(request.path))
  }

  override val toError: PartialFunction[Throwable, ApiError] = {
    case ApiException(error) => error
    case NonFatal(e) =>
      scribe.error("request handler threw exception")
      scribe.error(e)
      InternalServerError
  }

  override def publishEvent(event: ApiEvent) = distributor.publish(event)

  override def triggeredEvents(event: ApiEvent, state: State): Future[Seq[ApiEvent]] = stateInterpreter.triggeredEvents(state, event)

  override def onEvent(event: ApiEvent, state: State) = stateInterpreter.onEvent(state, event)

  override def onClientConnect(sender: EventSender[ApiEvent], state: State) = {
    scribe.info(s"client started: $state")
    distributor.subscribe(sender)
    Future.successful(state)
  }

  override def onClientDisconnect(sender: EventSender[ApiEvent], state: State) = {
    scribe.info(s"client stopped: $state")
    distributor.unsubscribe(sender)
  }

  override def onClientInteraction(sender: EventSender[ApiEvent], state: State, newState: State) = {
    val events = stateChangeEvents(state, newState)
    events.foreach(_.foreach(sender.send))
  }
}

object Server {
  import scala.concurrent.ExecutionContext.Implicits.global

  private val ws = {
    val db = Db(Config.db)
    val dsl = new GuardDsl(db, Config.auth.enableImplicit)
    val stateInterpreter = new StateInterpreter(db)

    def api(holder: StateHolder[State, ApiEvent]) =
      AutowireServer.route[Api](new ApiImpl(holder, dsl, db)) orElse
      AutowireServer.route[AuthApi](new AuthApiImpl(holder, dsl, db))

    new WebsocketServer[ApiEvent, ApiError, State](new ApiRequestHandler(new EventDistributor, stateInterpreter, api _))
  }

  private val route = (path("ws") & get) {
    ws.websocketHandler
  } ~ (path("health") & get) {
    complete("ok")
  }

  def run(port: Int) = ws.run(route, "0.0.0.0", port).foreach { binding =>
    scribe.info(s"Server online at ${binding.localAddress}")
  }
}
