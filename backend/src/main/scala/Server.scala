package wust.backend

import java.nio.ByteBuffer

import akka.http.scaladsl.server.Directives._
import autowire.Core.Request
import boopickle.Default._
import wust.api._
import wust.ids._
import wust.backend.auth._
import wust.backend.config.Config
import wust.db.Db
import wust.framework._
import wust.framework.state.StateHolder
import wust.util.{ Pipe, RichFuture }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Success, Failure }
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

  override def publishEvent(sender: EventSender[ApiEvent], event: ApiEvent) = {
    // do not send graphchange events to origin of event
    val origin = event match {
      case e @ NewGraphChanges(_) => Some(sender)
      case _                      => None
    }
    distributor.publish(origin, event)
  }

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

  override def onClientInteraction(state: State, newState: State) = stateChangeEvents(state, newState)
}

object Server {
  import scala.concurrent.ExecutionContext.Implicits.global

  private val ws = {
    val db = Db(Config.db)
    val stateInterpreter = new StateInterpreter(db)

    def api(holder: StateHolder[State, ApiEvent]) = {
      val dsl = new GuardDsl(db, Config.auth.enableImplicit)
      AutowireServer.route[Api](new ApiImpl(holder, dsl, db)) orElse
        AutowireServer.route[AuthApi](new AuthApiImpl(holder, dsl, db))
    }

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
