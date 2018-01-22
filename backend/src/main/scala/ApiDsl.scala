package wust.backend

import scala.concurrent.{ExecutionContext, Future}
import wust.api.{ApiEvent, ApiError}, ApiError.HandlerFailure
import cats.implicits._

//TODO move to sloth/mycelium/apidsl project as opionionated dsl?

object ApiData {
  type Action[+T] = Either[HandlerFailure, T]
  case class Transformation(state: Option[State], events: Seq[ApiEvent])
  case class Effect[+T](transformation: Transformation, action: Action[T])
}

case class ApiFunction[T](f: ApiFunction.Arguments => ApiFunction.Response[T]) extends AnyVal {
  import ApiFunction._, ApiData._

  def apply(state: Future[State], combine: ApiFunction.StateEventCombinator)(implicit ec: ExecutionContext): Response[T] = f(Arguments(state, combine))

  //TODO why does cats.syntax.functor not work?
  def map[R](f: T => R)(implicit ec: ExecutionContext): ApiFunction[R] = apiFunctionFunctor.map(this)(f)

  def redirect(f: State => Future[Transformation])(implicit ec: ExecutionContext): ApiFunction[T] = ApiFunction { data =>
    val transformation = data.state.flatMap(f)
    def newState = applyTransformationToState(data.state, data.combine, transformation)
    val response = apply(newState, data.combine)
    val newValue = prependEventsInValue(response.value, transformation.map(_.events))
    response.copy(value = newValue)
  }
}
object ApiFunction {
  import ApiData._

  type StateEventCombinator = (State, Seq[ApiEvent]) => State
  case class Arguments(state: Future[State], combine: StateEventCombinator)
  case class ReturnValue[T](result: Either[HandlerFailure, T], events: Seq[ApiEvent])
  case class Response[T](state: Future[State], value: Future[ReturnValue[T]])
  object Response {
    def apply[T](oldState: Future[State], action: Future[Action[T]])(implicit ec: ExecutionContext): Response[T] = {
      Response(oldState, action.map(action => ReturnValue(action, Seq.empty)))
    }
    def apply[T](oldState: Future[State], combine: StateEventCombinator, effect: Future[Effect[T]])(implicit ec: ExecutionContext): Response[T] = {
      val newState = applyTransformationToState(oldState, combine, effect.map(_.transformation))
      Response(newState, effect.map(e => ReturnValue(e.action, e.transformation.events)))
    }
  }

  protected def applyTransformationToState(state: Future[State], combine: StateEventCombinator, transformation: Future[Transformation])(implicit ec: ExecutionContext): Future[State] = for {
    t <- transformation
    state <- t.state.fold(state)(Future.successful)
  } yield if (t.events.isEmpty) state else combine(state, t.events)

  protected def prependEventsInValue[T](value: Future[ReturnValue[T]], events: Future[Seq[ApiEvent]])(implicit ec: ExecutionContext): Future[ReturnValue[T]] = for {
    events <- events
    value <- value
  } yield value.copy(events = events ++ value.events)

  implicit val apiReturnValueFunctor = cats.derive.functor[ReturnValue]
  implicit def apiResponseFunctor(implicit ec: ExecutionContext) = cats.derive.functor[Response]
  implicit def apiFunctionFunctor(implicit ec: ExecutionContext) = cats.derive.functor[ApiFunction]
}

trait ApiDsl {
  sealed trait ApiFunctionFactory[F[_]] {
    def apply[T](f: State => Future[F[T]])(implicit ec: ExecutionContext): ApiFunction[T]
    def apply[T](f: => Future[F[T]])(implicit ec: ExecutionContext): ApiFunction[T]
  }
  object Action extends ApiFunctionFactory[ApiData.Action] {
    def apply[T](f: State => Future[ApiData.Action[T]])(implicit ec: ExecutionContext): ApiFunction[T] = ApiFunction(d => ApiFunction.Response(d.state, d.state.flatMap(f)))
    def apply[T](f: => Future[ApiData.Action[T]])(implicit ec: ExecutionContext): ApiFunction[T] = ApiFunction(d => ApiFunction.Response(d.state, f))
  }
  object Effect extends ApiFunctionFactory[ApiData.Effect] {
    def apply[T](f: State => Future[ApiData.Effect[T]])(implicit ec: ExecutionContext): ApiFunction[T] = ApiFunction(d => ApiFunction.Response(d.state, d.combine, d.state.flatMap(f)))
    def apply[T](f: => Future[ApiData.Effect[T]])(implicit ec: ExecutionContext): ApiFunction[T] = ApiFunction(d => ApiFunction.Response(d.state, d.combine, f))
  }
  object Returns {
    def apply[T](state: State, result: T, events: Seq[ApiEvent]): ApiData.Effect[T] = ApiData.Effect(Transformation(state, events), apply(result))
    def apply[T](state: State, result: T): ApiData.Effect[T] = apply(state, result, Seq.empty)
    def apply[T](result: T, events: Seq[ApiEvent] = Seq.empty): ApiData.Effect[T] = ApiData.Effect(Transformation(events), Right(result))
    def apply[T](result: T): ApiData.Action[T] = Right(result)

    def error(failure: HandlerFailure, events: Seq[ApiEvent] = Seq.empty): ApiData.Effect[Nothing] = ApiData.Effect(Transformation(events), Left(failure))
    def error(failure: HandlerFailure): ApiData.Action[Nothing] = Left(failure)
  }
  object Transformation {
    def apply(state: State, events: Seq[ApiEvent] = Seq.empty): ApiData.Transformation = ApiData.Transformation(Some(state), events)
    def apply(events: Seq[ApiEvent]): ApiData.Transformation = ApiData.Transformation(None, events)
    def empty = ApiData.Transformation(None, Seq.empty)
  }

  implicit def ValueIsAction[T](value: T): ApiData.Action[T] = Returns(value)
  implicit def FailureIsAction[T](failure: HandlerFailure): ApiData.Action[T] = Returns.error(failure)
  implicit def FutureValueIsAction[T](value: Future[T])(implicit ec: ExecutionContext): Future[ApiData.Action[T]] = value.map(ValueIsAction)
  implicit def FutureFailureIsAction[T](failure: Future[HandlerFailure])(implicit ec: ExecutionContext): Future[ApiData.Action[T]] = failure.map(FailureIsAction)
}
object ApiDsl extends ApiDsl
