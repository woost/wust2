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
import sloth.server.{Server => SlothServer, _}
import mycelium.server._
import wust.util.{ Pipe, RichFuture }
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

import scala.util.{ Success, Failure }
import scala.util.control.NonFatal

class ApiRequestHandler(distributor: EventDistributor, stateInterpreter: StateInterpreter, api: Router[ByteBuffer, ApiFunction])(implicit ec: ExecutionContext) extends FullRequestHandler[ByteBuffer, ApiEvent, RequestEvent, ApiError, State] {
  import stateInterpreter._

  def initialState = Future.successful(State.initial)

  override def onRequest(client: NotifiableClient[RequestEvent], originalState: Future[State], path: List[String], payload: ByteBuffer): Response = {
    val state = validateState(originalState)
    scribe.info(s"Incoming request ($path)")
    val response = api(Request(path, payload)) match {
      case Left(slothError) =>
        val error = ApiError.ServerError(slothError.toString)
        Response(state, Future.successful(ReturnValue(Left(error), Seq.empty)))
      case Right(apiFunction) =>
        val apiResponse = apiFunction.run(state)
        val newState = apiResponse.state
        //.recover(handleUserException andThen Left.apply) //TODO: move to apidsl, not enough here
        val returnValue = apiResponse.value.map { r => ReturnValue(r.result, filterAndDistributeEvents(client)(r.events)) }
        Response(newState, returnValue)
    }

    response.value.foreach { value =>
      value.result match {
        case Right(_) => scribe.info(s"Responding to request ($path) / events: ${value.events}")
        case Left(error) => scribe.info(s"Error in request ($path): $error / events: ${value.events}")
      }
    }

    response
  }

  override def onEvent(client: NotifiableClient[RequestEvent], originalState: Future[State], requestEvent: RequestEvent): Reaction = {
    val state = validateState(originalState)
    scribe.info(s"client got event: $client")
    val newEvents = state.flatMap(triggeredEvents(_, requestEvent))
    val newState = for {
      events <- newEvents
      state <- state
    } yield State.applyEvents(state, events)

    Reaction(newState, newEvents)
  }

  override def onClientConnect(client: NotifiableClient[RequestEvent], state: Future[State]): Unit = {
    scribe.info(s"client started: $client")
    distributor.subscribe(client)
  }

  override def onClientDisconnect(client: NotifiableClient[RequestEvent], state: Future[State], reason: DisconnectReason): Unit = {
    scribe.info(s"client stopped ($reason): $client")
    distributor.unsubscribe(client)
  }

  // we check whether this jwt token is expired. if it is, we return a failed state, which will force close the websocket connection from the server side. the client can then check that the token is indeed expired and should prompt the user. meanwhile he can then work as an assumed/implicit user again.
  private def validateState(state: Future[State]): Future[State] = state.flatMap { state =>
    state.auth match {
      case auth: Authentication.Verified if JWT.isExpired(auth) => Future.failed(new Exception("Authentication expired"))
      case _ => Future.successful(state)
    }
  }

  // 
  private def filterAndDistributeEvents[T](client: NotifiableClient[RequestEvent])(events: Seq[ApiEvent]): Seq[ApiEvent.Private] = {
    val (privateEvents, publicEvents) = ApiEvent.separateByScope(events)
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
