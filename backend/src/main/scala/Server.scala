package wust.backend

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import boopickle.Default._
import wust.api._
import wust.ids._
import wust.backend.auth._
import wust.backend.config.Config
import wust.db.Db
import sloth.core._
import sloth.mycelium._
import sloth.boopickle._
import sloth.server.{Server => SlothServer, _}
import mycelium.server._
import wust.util.{ Pipe, RichFuture }
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

import scala.util.{ Success, Failure }
import scala.util.control.NonFatal

class ApiRequestHandler(distributor: EventDistributor, stateInterpreter: StateInterpreter, api: PartialFunction[Request[ByteBuffer], Either[SlothServerFailure, ApiResult.Function[ByteBuffer]]])(implicit ec: ExecutionContext) extends RequestHandler[ByteBuffer, ApiEvent, RequestEvent, ApiError, State] {
  import stateInterpreter._

  private val initialState = Future.successful(State.initial)

  private def stateOpsToState(currentState: Future[State]): ApiResult.StateOps => Future[State] = {
    case ApiResult.KeepState => currentState
    case ApiResult.ReplaceState(nextState) => nextState
  }

  private def filterAndDistributeEvents[T](client: NotifiableClient[RequestEvent])(events: Seq[ApiEvent]): Seq[ApiEvent] = {
    val (privateEvents, publicEvents) = ApiEvent.separate(events)
    distributor.publish(client, publicEvents)
    privateEvents
  }

  private def handleUserException[T](fallback: => T): PartialFunction[Throwable, T] = {
    case NonFatal(e) =>
      scribe.error("request handler threw exception")
      scribe.error(e)
      fallback
  }

  //TODO we should not change the state on every request and track a graph in each connectedclient, we should instead have use our db or a cache to retrieve info about the graph.
  private def reaction(oldState: Future[State], newState: Future[State], events: Future[Seq[ApiEvent]]): (Option[Future[State]], Future[Seq[ApiEvent]]) = {
    val next = for {
      oldState <- oldState
      newState <- newState
      changeEvents <- stateChangeEvents(oldState, newState)
      events <- events
      allEvents = changeEvents ++ events
      nextState = applyEventsToState(newState, allEvents)
    } yield (nextState, allEvents)

    (Some(next.map(_._1)), next.map(_._2))
  }

  override def onRequest(client: NotifiableClient[RequestEvent], originalState: Future[State], path: List[String], payload: ByteBuffer): Response = {
    val state = originalState.map(validate)
    api.lift(Request(path, payload)) match {
      case None =>
        val error = ApiError.NotFound(path)
        Response(Future.successful(Left(error)))
      case Some(Left(slothError)) =>
        val error = ApiError.ProtocolError(slothError.toString)
        Response(Future.successful(Left(error)))
      case Some(Right(stateToResult)) =>
        val apiResult = state.map(stateToResult)

        val apiStateOps = apiResult
          .map(_.stateOps)
          .recover(handleUserException(ApiResult.KeepState))
        val apiCall = apiResult
          .flatMap(_.call)
          .recover(handleUserException(ApiCall.fail(ApiError.InternalServerError)))

        val newState = apiStateOps.flatMap(stateOpsToState(state))
        val newEvents = apiCall.map(call => filterAndDistributeEvents(client)(call.events))

        val finalResult = apiCall.map(_.result)
        val (finalState, finalEvents) = reaction(originalState, newState, newEvents)
        Response(finalResult, finalState, finalEvents)
      }
  }

  override def onEvent(client: NotifiableClient[RequestEvent], originalState: Future[State], requestEvent: RequestEvent): Reaction = {
    scribe.info(s"client got event: $client")
    val state = originalState.map(validate)
    val events = state.flatMap(triggeredEvents(_, requestEvent))

    val (finalState, finalEvents) = reaction(originalState, state, events)
    Reaction(finalState, finalEvents)
  }

  override def onClientConnect(client: NotifiableClient[RequestEvent]): InitialState = {
    scribe.info(s"client started: $client")
    distributor.subscribe(client)

    // send an initial graph
    val initialEvents = stateInterpreter
      .getInitialGraph()
      .map(graph => Seq(ApiEvent.ReplaceGraph(graph)))

    InitialState(initialState, initialEvents)
  }

  override def onClientDisconnect(client: NotifiableClient[RequestEvent], state: Future[State]): Unit = {
    scribe.info(s"client stopped: $client")
    distributor.unsubscribe(client)
  }
}

//TODO: why do we need this? as we can see, these picklers can be resolved implicitly, but somehow we need to use them explicitly.
object HelpMePickle {
  val graphChanges = implicitly[Pickler[List[wust.graph.GraphChanges]]]
  val apiEvents = implicitly[Pickler[List[ApiEvent]]]
}

object WebsocketFactory {
  import DbConversions._

  def apply(config: Config)(implicit ec: ExecutionContext, system: ActorSystem) = {
    implicit val apiCallFunctor = cats.derive.functor[ApiCall]
    implicit val apiResultFunctor = cats.derive.functor[ApiResult]
    implicit val apiResultFunctionFunctor = cats.derive.functor[ApiResult.Function]

    val db = Db(config.db)
    val jwt = JWT(config.auth.secret, config.auth.tokenLifetime)
    val stateInterpreter = new StateInterpreter(jwt, db)
    val guardDsl = GuardDsl(jwt, db)

    //TODO
    implicit val todo1 = HelpMePickle.graphChanges
    implicit val todo2 = HelpMePickle.apiEvents
    val server = SlothServer[ByteBuffer, ApiResult.Function]
    val api =
      server.route[Api[ApiResult.Function]](new ApiImpl(guardDsl, db)) orElse
        server.route[AuthApi[ApiResult.Function]](new AuthApiImpl(guardDsl, db, jwt))

    val requestHandler = new ApiRequestHandler(new EventDistributor(db), stateInterpreter, api)
    val serverConfig = ServerConfig(bufferSize = config.server.clientBufferSize, overflowStrategy = OverflowStrategy.fail)
    () => WebsocketServerFlow(serverConfig, requestHandler)
  }
}

object Server {
  import akka.http.scaladsl.server.RouteResult._
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.Http

  def run(config: Config) = {
    implicit val system = ActorSystem("server")
    implicit val materializer = ActorMaterializer()
    import system.dispatcher

    val websocketFlowFactory = WebsocketFactory(config)
    val route = (path("ws") & get) {
      handleWebSocketMessages(websocketFlowFactory())
    } ~ (path("health") & get) {
      complete("ok")
    }

    Http().bindAndHandle(route, interface = "0.0.0.0", port = config.server.port).onComplete {
      case Success(binding) => {
        val separator = "\n" + ("#" * 50)
        val readyMsg = s"\n##### Server online at ${binding.localAddress} #####"
        scribe.info(s"$separator$readyMsg$separator")
      }
      case Failure(err) => scribe.error(s"Cannot start server: $err")
    }
  }
}
