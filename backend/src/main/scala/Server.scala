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

class ApiRequestHandler(distributor: EventDistributor, stateInterpreter: StateInterpreter, api: StateHolder[State, ApiEvent] => PartialFunction[Request[ByteBuffer], Either[SlothServerFailure, Future[ByteBuffer]]])(implicit ec: ExecutionContext) extends RequestHandler[ByteBuffer, ApiEvent, RequestEvent, ApiError, State] {
  import stateInterpreter._

  private val toError: PartialFunction[Throwable, ApiError] = {
    case ApiException(error) => error
    case NonFatal(e) =>
      scribe.error("request handler threw exception")
      scribe.error(e)
      ApiError.InternalServerError
  }

  //TODO we should not change the state on every request and track a graph in each connectedclient, we should instead have use our db or a cache to retrieve info about the graph.
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

  override def onRequest(client: NotifiableClient[RequestEvent], originalState: Future[State], path: List[String], payload: ByteBuffer) = {
    val state = originalState.map(validate)
    val holder = new StateHolder[State, ApiEvent](state)
    val handler = api(holder).lift
    val result = handler(Request(path, payload)) match {
      case None =>
        val error = ApiError.NotFound(path)
        Response(Future.successful(Left(error)))
      case Some(Left(slothError)) =>
        val error = ApiError.ProtocolError(slothError.toString)
        Response(Future.successful(Left(error)))
      case Some(Right(response)) =>
        val newState = holder.state
        val newEvents = holder.events.map { events =>
          //TODO: helper for val t = collectType[T], val (a,b) = collectType2[A,B]
          // send out public events
          val publicEvents = events collect { case e: ApiEvent.Public => e }
          distributor.publish(client, publicEvents)

          // return private events
          val privateEvents = events collect { case e: ApiEvent.Private => e }
          privateEvents
        }

        val res = response.map(Right(_)).recover(toError andThen (Left(_)))
        Response(res, reaction(originalState, newState, newEvents))
    }

    result
  }

  override def onEvent(client: NotifiableClient[RequestEvent], originalState: Future[State], requestEvent: RequestEvent): Reaction = {
    scribe.info(s"client got event: $client")
    val state = originalState.map(validate)
    val events = state.flatMap(triggeredEvents(_, requestEvent))
    reaction(originalState, state, events)
  }

  override def onClientConnect(client: NotifiableClient[RequestEvent]): Reaction = {
    scribe.info(s"client started: $client")
    distributor.subscribe(client)

    // send an initial graph
    val initialEvents = stateInterpreter
      .getInitialGraph()
      .map(graph => Seq(ApiEvent.ReplaceGraph(graph)))

    Reaction(Future.successful(State.initial), initialEvents)
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
    val db = Db(config.db)
    val jwt = JWT(config.auth.secret, config.auth.tokenLifetime)
    val stateInterpreter = new StateInterpreter(db, jwt)

    //TODO
    implicit val todo1 = HelpMePickle.graphChanges
    implicit val todo2 = HelpMePickle.apiEvents
    val server = SlothServer[ByteBuffer, Future]
    //TODO: get rid of stateholder, refactor to returning state functions with events and result
    def api(holder: StateHolder[State, ApiEvent]) = {
      val dsl = GuardDsl(jwt, db, config.auth.enableImplicit)
      server.route[Api](new ApiImpl(holder, dsl, db)) orElse
        server.route[AuthApi](new AuthApiImpl(holder, dsl, db, jwt))
    }

    val requestHandler = new ApiRequestHandler(new EventDistributor(db), stateInterpreter, api _)
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
