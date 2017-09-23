package wust.backend

import java.nio.ByteBuffer

import akka.http.scaladsl.server.Directives._
import akka.actor.ActorSystem
import autowire.Core.Request
import boopickle.Default._
import wust.api._
import wust.ids._
import wust.backend.auth._
import wust.backend.config.Config
import wust.db.Db
import wust.framework._
import wust.util.{ Pipe, RichFuture }

import scala.concurrent.{ ExecutionContext, Future }

import scala.util.{ Success, Failure }
import scala.util.control.NonFatal

class ApiRequestHandler(distributor: EventDistributor, stateInterpreter: StateInterpreter, api: StateHolder[State, ApiEvent] => PartialFunction[Request[ByteBuffer], Future[ByteBuffer]])(implicit ec: ExecutionContext) extends RequestHandler[ApiEvent, RequestEvent, ApiError, State] {
  import stateInterpreter._

  private val toError: PartialFunction[Throwable, ApiError] = {
    case ApiException(error) => error
    case NonFatal(e) =>
      scribe.error("request handler threw exception")
      scribe.error(e)
      InternalServerError
  }

  private def reaction(oldState: Future[State], newState: Future[State], events: Future[Seq[ApiEvent]]) = {
    val next: Future[(State, Seq[ApiEvent])] = for {
      oldState <- oldState
      newState <- newState
      changeEvents <- stateChangeEvents(oldState, newState)
      events <- events
      allEvents = changeEvents ++ events
      nextState = applyEventsToState(newState, allEvents)
    } yield (nextState, allEvents)

    Reaction(next.map(_._1), next.map(_._2))
  }

  override def onRequest(client: ClientIdentity, originalState: Future[State], request: Request[ByteBuffer]) = {
    val state = originalState.map(validate)
    val holder = new StateHolder[State, ApiEvent](state)
    val handler = api(holder).lift
    val result = handler(request) match {
      case None =>
        val error = NotFound(request.path)
        Response(Reaction(state), Future.successful(Left(error)))
      case Some(response) =>
        val newState = holder.state
        val newEvents = holder.events.map { events =>
          // send out public events
          val publicEvents = events collect { case e: ApiEvent.Public => e }
          distributor.publish(client, publicEvents)

          // return private events
          val privateEvents = events collect { case e: ApiEvent.Private => e }
          privateEvents
        }

        val res = response.map(Right(_)).recover(toError andThen (Left(_)))
        Response(reaction(originalState, newState, newEvents), res)
    }

    result
  }

  override def onEvent(client: ClientIdentity, originalState: Future[State], requestEvent: RequestEvent): Reaction = {
    val state = originalState.map(validate)
    val events = state.flatMap(triggeredEvents(_, requestEvent))
    reaction(originalState, state, events)
  }

  override def onClientConnect(client: NotifiableClient[RequestEvent]): State = {
    scribe.info(s"client started")
    distributor.subscribe(client)
    State.initial
  }

  override def onClientDisconnect(client: NotifiableClient[RequestEvent], state: Future[State]): Unit = {
    scribe.info(s"client stopped: $state")
    distributor.unsubscribe(client)
  }
}

object WebsocketFactory {
  import DbConversions._

  def apply(config: Config)(implicit ec: ExecutionContext, system: ActorSystem) = {
    val db = Db(config.db)
    val jwt = JWT(config.auth.secret, config.auth.tokenLifetime)
    val stateInterpreter = new StateInterpreter(db, jwt)

    def api(holder: StateHolder[State, ApiEvent]) = {
      val dsl = GuardDsl(jwt, db, config.auth.enableImplicit)
      AutowireServer.route[Api](new ApiImpl(holder, dsl, db)) orElse
        AutowireServer.route[AuthApi](new AuthApiImpl(holder, dsl, db, jwt))
    }

    val handler = new ApiRequestHandler(new EventDistributor(db), stateInterpreter, api _)
    new WebsocketServer(handler)
  }
}

object Server {
  import ExecutionContext.Implicits.global

  def run(config: Config) = {
    implicit val system = ActorSystem("server")
    val ws = WebsocketFactory(config)
    val route = (path("ws") & get) {
      ws.websocketHandler
    } ~ (path("health") & get) {
      complete("ok")
    }

    ws.run(route, "0.0.0.0", config.server.port).onComplete {
      case Success(binding) => scribe.info(s"Server online at ${binding.localAddress}")
      case Failure(err) => scribe.error(s"Cannot start server: $err")
    }
  }
}
