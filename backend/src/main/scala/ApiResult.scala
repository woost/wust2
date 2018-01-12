package wust.backend

import scala.concurrent.{ExecutionContext, Future}
import wust.api.{ApiEvent, ApiError}, ApiError.HandlerFailure

//TODO move to sloth project as opionionated dsl?

case class ApiResult[T](stateOps: ApiResult.StateOps, call: Future[ApiCall[T]])
object ApiResult {

  //TODO: different return types...
  // sealed trait Function[T]
  // case class ApiResultFunction[T](func: State => ApiResult[T]) extends Function[T]
  // case class ApiCallFunction[T](func: State => Future[ApiCall[T]]) extends Function[T]
  //- just apicall (safer because cannot accidentally run unnessecary sync code in state future),
  //- full apiresult (most powerful)
  //- for no-state dependency (faster as not depends on previous state future - just apicall or also apiresult?)

  type Function1[A, T] = A => ApiResult[T]
  type Function2[A, B, T] = (A, B) => ApiResult[T]
  type Function3[A, B, C, T] = (A, B, C) => ApiResult[T]
  type Function[T] = Function1[State, T]

  sealed trait StateOps
  case object KeepState extends StateOps
  case class ReplaceState(state: Future[State]) extends StateOps

  class ApiResultWithState[T](val state: Future[State], call: Future[ApiCall[T]]) extends ApiResult[T](ReplaceState(state), call)
  def apply[T](state: Future[State], call: Future[ApiCall[T]]): ApiResultWithState[T] = new ApiResultWithState[T](state, call)
  def apply[T](state: Future[State], call: ApiCall[T]): ApiResultWithState[T] = new ApiResultWithState[T](state, Future.successful(call))
  def apply[T](state: State, call: Future[ApiCall[T]]): ApiResultWithState[T] = new ApiResultWithState[T](Future.successful(state), call)
  def apply[T](state: State, call: ApiCall[T]): ApiResultWithState[T] = new ApiResultWithState[T](Future.successful(state), Future.successful(call))
  def apply[T](state: StateOps, call: ApiCall[T]): ApiResult[T] = ApiResult[T](state, Future.successful(call))

  implicit def FutureApiResultIsApiResult[T](res: Future[ApiResultWithState[T]])(implicit ec: ExecutionContext): ApiResult[T] = ApiResult(ReplaceState(res.flatMap(_.state)), res.flatMap(_.call))
  implicit def FutureApiCallIsApiResult[T](call: Future[ApiCall[T]]): ApiResult[T] = ApiResult(KeepState, call)
  implicit def ApiCallIsApiResult[T](call: ApiCall[T]): ApiResult[T] = ApiResult(KeepState, Future.successful(call))
  implicit def ResultIsApiResult[T](result: T): ApiResult[T] = ApiResult(KeepState, Future.successful(ApiCall(result)))
  implicit def FutureResultIsApiResult[T](result: Future[T])(implicit ec: ExecutionContext): ApiResult[T] = ApiResult(KeepState, result.map(ApiCall[T](_)))
}

case class ApiCall[T](result: Either[HandlerFailure, T], events: Seq[ApiEvent])
object ApiCall {
  def apply[T](result: T, events: Seq[ApiEvent] = Seq.empty): ApiCall[T] = ApiCall(Right(result), events)
  def withEventsIf(result: Boolean, events: Seq[ApiEvent] = Seq.empty): ApiCall[Boolean] = ApiCall(Right(result), if (result) events else Seq.empty)
  def fail[T](failure: HandlerFailure, events: Seq[ApiEvent] = Seq.empty): ApiCall[T] = ApiCall(Left(failure), events)

  implicit def ResultIsApiCall[T](result: T): ApiCall[T] = ApiCall[T](result)
  implicit def FutureResultIsFutureApiCall[T](result: Future[T])(implicit ec: ExecutionContext): Future[ApiCall[T]] = result.map(ApiCall(_))
}
