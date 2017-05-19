package wust.framework.state
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._

import scala.concurrent.{ ExecutionContext, Future }

case class RequestResponse[T, Event](result: T, events: Seq[Event] = Seq.empty)
case class StateEffect[State, T, Event](state: Future[State], response: Future[RequestResponse[T, Event]])

class StateHolder[State, Event](initialState: Future[State]) {
  private var actualState = initialState
  private var actualEvents = Future.successful(Seq.empty[Event])
  // TODO: private[framework] def state = actualState
  def state = actualState
  def events = actualEvents

  private def returnResult[T](response: Future[RequestResponse[T, Event]])(implicit ec: ExecutionContext): Future[T] = {
    //sideeffect: set events
    actualEvents = response.map(_.events)

    response.map(_.result)
  }

  def respondWithEvents[T](result: T, events: Event*) = new RequestResponse[T, Event](result, events)
  def respondWithEventsIf(result: Boolean, events: Event*) = result match {
    case true => new RequestResponse[Boolean, Event](result, events)
    case false => new RequestResponse[Boolean, Event](result, Seq.empty)
  }
  implicit def resultIsRequestResponse[T](result: T)(implicit ec: ExecutionContext): RequestResponse[T, Event] = RequestResponse(result)
  implicit def futureResultIsRequestResponse[T](result: Future[T])(implicit ec: ExecutionContext): Future[RequestResponse[T, Event]] = result.map(RequestResponse(_))
  implicit def resultFunctionIsExecuted[T](f: State => Future[T])(implicit ec: ExecutionContext): Future[T] = state.flatMap(f)
  implicit def responseFunctionIsExecuted[T](f: State => Future[RequestResponse[T, Event]])(implicit ec: ExecutionContext): Future[T] = returnResult(state.flatMap(f))
  implicit def effectFunctionIsExecuted[T](f: State => StateEffect[State, T, Event])(implicit ec: ExecutionContext): Future[T] = {
    val effect = state.map(f)
    val newState = effect.flatMap(_.state)
    val response = effect.flatMap(_.response)

    //sideeffect: set new state
    actualState = newState

    returnResult(response)
  }
}
