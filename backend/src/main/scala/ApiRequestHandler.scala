package wust.backend

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import boopickle.Default._
import wust.api._, serialize.Boopickle._
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

class ApiRequestHandler(distributor: EventDistributor, stateInterpreter: StateInterpreter, api: PartialFunction[Request[ByteBuffer], Either[SlothServerFailure, ApiFunction[ByteBuffer]]])(implicit ec: ExecutionContext) extends FullRequestHandler[ByteBuffer, ApiEvent, RequestEvent, ApiError, State] {
  import stateInterpreter._

  def initialReaction = {
    val initialState = Future.successful(State.initial)
    val initialEvents = stateInterpreter
      .getInitialGraph()
      .map(graph => Seq(ApiEvent.ReplaceGraph(graph)))

    Reaction(initialState, initialEvents)
  }

  override def onRequest(client: NotifiableClient[RequestEvent], state: Future[State], path: List[String], payload: ByteBuffer): Response = {
    api.lift(Request(path, payload)) match {
      case None =>
        val error = ApiError.NotFound(path)
        Response(Future.successful(Left(error)), Reaction(state))
      case Some(Left(slothError)) =>
        val error = ApiError.ProtocolError(slothError.toString)
        Response(Future.successful(Left(error)), Reaction(state))
      case Some(Right(apiFunction)) =>
        val apiReturn = apiFunction(state, applyEventsToState)
        val newState = apiReturn.state
        val result = apiReturn.result.recover(handleUserException andThen Left.apply)
        val newEvents = apiReturn.events.map(filterAndDistributeEvents(client))
        Response(result, Reaction(newState, newEvents))
      }
  }

  override def onEvent(client: NotifiableClient[RequestEvent], state: Future[State], requestEvent: RequestEvent): Reaction = {
    scribe.info(s"client got event: $client")
    val newEvents = state.flatMap(triggeredEvents(_, requestEvent))
    val newState = for {
      events <- newEvents
      state <- state
    } yield applyEventsToState(state, events)

    Reaction(newState, newEvents)
  }

  override def onClientConnect(client: NotifiableClient[RequestEvent], state: Future[State]): Unit = {
    scribe.info(s"client started: $client")
    distributor.subscribe(client)
  }

  override def onClientDisconnect(client: NotifiableClient[RequestEvent], state: Future[State]): Unit = {
    scribe.info(s"client stopped: $client")
    distributor.unsubscribe(client)
  }

  private def filterAndDistributeEvents[T](client: NotifiableClient[RequestEvent])(events: Seq[ApiEvent]): Seq[ApiEvent] = {
    val (privateEvents, publicEvents) = ApiEvent.separate(events)
    distributor.publish(client, publicEvents)
    privateEvents
  }

  private val handleUserException: PartialFunction[Throwable, ApiError.HandlerFailure] = {
    case NonFatal(e) =>
      scribe.error("request handler threw exception")
      scribe.error(e)
      ApiError.InternalServerError
  }
}
