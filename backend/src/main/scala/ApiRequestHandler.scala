package wust.backend

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import wust.api._, serialize.Boopickle._
import wust.ids._
import wust.backend.auth._
import wust.backend.config.Config
import wust.db.Db
import wust.util.time._
import sloth.core._
import sloth.mycelium._
import sloth.server.{Server => SlothServer, _}
import mycelium.server._
import wust.util.{ Pipe, RichFuture }
import wust.util.LogHelper.requestLogLine
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

import scala.util.{ Success, Failure }
import scala.util.control.NonFatal

//TODO: filter auth in args and events from log
class ApiRequestHandler(distributor: EventDistributor, stateInterpreter: StateInterpreter, api: Router[ByteBuffer, ApiFunction])(implicit ec: ExecutionContext) extends FullRequestHandler[ByteBuffer, ApiEvent, RequestEvent, ApiError, State] {
  import stateInterpreter._

  def initialState = Future.successful(State.initial)

  override def onClientConnect(client: NotifiableClient[RequestEvent], state: Future[State]): Unit = {
    scribe.info(s"${clientDesc(client)} started")
    distributor.subscribe(client)
  }

  override def onClientDisconnect(client: NotifiableClient[RequestEvent], state: Future[State], reason: DisconnectReason): Unit = {
    scribe.info(s"${clientDesc(client)} stopped: $reason")
    distributor.unsubscribe(client)
  }

  override def onRequest(client: NotifiableClient[RequestEvent], originalState: Future[State], path: List[String], payload: ByteBuffer): Response = {
    scribe.info(s"${clientDesc(client)} <-- $path")
    val watch = StopWatch.started

    val state = validateState(originalState)
    val response = api(Request(path, payload)) match {
      case ServerResult.Success(arguments, apiFunction) =>
        val apiResponse = apiFunction.run(state)
        val newState = apiResponse.state
        val returnValue = apiResponse.value.map { value =>
          val rawResult = value.result.map(_.raw)
          val serializedResult = value.result.map(_.serialized)
          val events = filterAndDistributeEvents(client)(value.events)
          scribe.info(s"${clientDesc(client)} --> ${requestLogLine(path, arguments, rawResult)} / $events. Took ${watch.readHuman}.")
          ReturnValue(serializedResult, events)
        }
        Response(newState, returnValue)
      case ServerResult.Failure(arguments, slothError) =>
        val error = ApiError.ServerError(slothError.toString)
        scribe.warn(s"${clientDesc(client)} --> ${requestLogLine(path, arguments, error)}. Took ${watch.readHuman}.")
        Response(state, Future.successful(ReturnValue(Left(error), Seq.empty)))
    }

    response
  }

  override def onEvent(client: NotifiableClient[RequestEvent], originalState: Future[State], requestEvent: RequestEvent): Reaction = {
    scribe.info(s"${clientDesc(client)} got event: $requestEvent")
    val state = validateState(originalState)
    val result = for {
      state <- state
      events <- triggeredEvents(state, requestEvent)
    } yield (State.applyEvents(state, events), events)

    val newState = result.map(_._1)
    val newEvents = result.map(_._2)
    Reaction(newState, newEvents)
  }

  private def clientDesc(client: NotifiableClient[RequestEvent]): String = s"Client(${Integer.toString(client.hashCode, 36)})"

  // we check whether this jwt token is expired. if it is, we return a failed state, which will force close the websocket connection from the server side. the client can then check that the token is indeed expired and should prompt the user. meanwhile he can then work as an assumed/implicit user again.
  private def validateState(state: Future[State]): Future[State] = state.flatMap { state =>
    state.auth match {
      case auth: Authentication.Verified if JWT.isExpired(auth) => Future.failed(new Exception("Authentication expired"))
      case _ => Future.successful(state)
    }
  }

  // returns all private events, and publishes all public events to the eventdistributor.
  private def filterAndDistributeEvents[T](client: NotifiableClient[RequestEvent])(events: Seq[ApiEvent]): Seq[ApiEvent.Private] = {
    val (privateEvents, publicEvents) = ApiEvent.separateByScope(events)
    distributor.publish(client, publicEvents)
    privateEvents
  }
}
