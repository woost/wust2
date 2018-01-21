package wust.backend

import scala.concurrent.{ExecutionContext, Future}
import wust.api.{ApiEvent, ApiError}, ApiError.HandlerFailure
import cats.implicits._

//TODO move to sloth/mycelium/apidsl project as opionionated dsl?

object ApiData {
  case class Transformation(state: Option[State], events: Seq[ApiEvent])
  case class Action[+T](result: Either[HandlerFailure, T]) extends AnyVal
  case class Effect[+T](transformation: Transformation, action: Action[T])

  implicit val apiActionFunctor = cats.derive.functor[Action]
  implicit val apiEffectFunctor = cats.derive.functor[Effect]

  //TODO: why do we need these Future[F[T]] types and implicits?
  type FutureAction[T] = Future[Action[T]]
  type FutureEffect[T] = Future[Effect[T]]
  implicit def futureApiActionFunctor(implicit ec: ExecutionContext) = cats.derive.functor[FutureAction]
  implicit def futureApiEffectFunctor(implicit ec: ExecutionContext) = cats.derive.functor[FutureEffect]
}

sealed trait ApiFunction[T] extends Any {
  def apply(state: Future[State], combine: ApiFunction.StateEventCombinator)(implicit ec: ExecutionContext): ApiFunction.Response[T]

  def redirect(transform: State => Future[ApiData.Transformation]): ApiFunction[T] = ApiFunction.Redirect(this, transform)
}
object ApiFunction {
  type StateEventCombinator = (State, Seq[ApiEvent]) => State
  case class ReturnValue[T](result: Either[HandlerFailure, T], events: Seq[ApiEvent])
  case class Response[T](state: Future[State], value: Future[ReturnValue[T]])
  object Response {
    def apply[T](oldState: Future[State], action: Future[ApiData.Action[T]])(implicit ec: ExecutionContext): Response[T] = {
      Response(oldState, action.map(action => ReturnValue(action.result, Seq.empty)))
    }
    def apply[T](oldState: Future[State], combine: StateEventCombinator, effect: Future[ApiData.Effect[T]])(implicit ec: ExecutionContext): Response[T] = {
      val newState = applyTransformationToState(oldState, combine, effect.map(_.transformation))
      Response(newState, effect.map(e => ReturnValue(e.action.result, e.transformation.events)))
    }
  }

  case class Action[T](f: State => Future[ApiData.Action[T]]) extends AnyVal with ApiFunction[T] {
    def apply(state: Future[State], combine: StateEventCombinator)(implicit ec: ExecutionContext) = Response(state, state.flatMap(f))
  }
  case class IndependentAction[T](f: () => Future[ApiData.Action[T]]) extends AnyVal with ApiFunction[T] {
    def apply(state: Future[State], combine: StateEventCombinator)(implicit ec: ExecutionContext) = Response(state, f())
  }
  case class Effect[T](f: State => Future[ApiData.Effect[T]]) extends AnyVal with ApiFunction[T] {
    def apply(state: Future[State], combine: StateEventCombinator)(implicit ec: ExecutionContext) = Response(state, combine, state.flatMap(f))
  }
  case class IndependentEffect[T](f: () => Future[ApiData.Effect[T]]) extends AnyVal with ApiFunction[T] {
    def apply(state: Future[State], combine: StateEventCombinator)(implicit ec: ExecutionContext) = Response(state, combine, f())
  }
  case class Redirect[T](f: ApiFunction[T], transform: State => Future[ApiData.Transformation]) extends ApiFunction[T] {
    def apply(state: Future[State], combine: StateEventCombinator)(implicit ec: ExecutionContext) = {
      val transformation = state.flatMap(transform)
      def newState = applyTransformationToState(state, combine, transformation)
      val response = f(newState, combine)
      val newValue = for {
        t <- transformation
        value <- response.value
      } yield value.copy(events = t.events ++ value.events)

      response.copy(value = newValue)
    }
  }

  private def applyTransformationToState(state: Future[State], combine: StateEventCombinator, transformation: Future[ApiData.Transformation])(implicit ec: ExecutionContext): Future[State] = for {
    t <- transformation
    state <- t.state.fold(state)(Future.successful)
  } yield if (t.events.isEmpty) state else combine(state, t.events)

  implicit def apiReturnValueFunctor(implicit ec: ExecutionContext) = cats.derive.functor[ReturnValue]
  implicit def apiResponseFunctor(implicit ec: ExecutionContext) = cats.derive.functor[Response]
  implicit def apiFunctionFunctor(implicit ec: ExecutionContext) = cats.derive.functor[ApiFunction]
}

trait ApiDsl {
  sealed trait ApiFunctionFactory[F[_]] {
    def apply[T](f: State => Future[F[T]]): ApiFunction[T]
    def apply[T](f: => Future[F[T]]): ApiFunction[T]
  }
  object Action extends ApiFunctionFactory[ApiData.Action] {
    def apply[T](f: State => Future[ApiData.Action[T]]): ApiFunction[T] = ApiFunction.Action(f)
    def apply[T](f: => Future[ApiData.Action[T]]): ApiFunction[T] = ApiFunction.IndependentAction(() => f)
  }
  object Effect extends ApiFunctionFactory[ApiData.Effect] {
    def apply[T](f: State => Future[ApiData.Effect[T]]): ApiFunction[T] = ApiFunction.Effect(f)
    def apply[T](f: => Future[ApiData.Effect[T]]): ApiFunction[T] = ApiFunction.IndependentEffect(() => f)
  }
  object Returns {
    def raw[T](state: State, result: T, events: Seq[ApiEvent] = Seq.empty): ApiData.Effect[T] = ApiData.Effect(Transformation.raw(state, events), apply(result))
    def apply[T](result: T, events: Seq[ApiEvent] = Seq.empty): ApiData.Effect[T] = ApiData.Effect(Transformation(events), apply(result))
    def apply[T](result: T): ApiData.Action[T] = ApiData.Action(Right(result))

    def error(failure: HandlerFailure, events: Seq[ApiEvent] = Seq.empty): ApiData.Effect[Nothing] = ApiData.Effect(Transformation(events), error(failure))
    def error(failure: HandlerFailure): ApiData.Action[Nothing] = ApiData.Action(Left(failure))
  }
  object Transformation {
    def raw(state: State, events: Seq[ApiEvent] = Seq.empty): ApiData.Transformation = ApiData.Transformation(Some(state), events)
    def apply(events: Seq[ApiEvent]): ApiData.Transformation = ApiData.Transformation(None, events)
  }

  implicit def ValueIsAction[T](value: T): ApiData.Action[T] = Returns(value)
  implicit def FailureIsAction[T](failure: HandlerFailure): ApiData.Action[T] = Returns.error(failure)
  implicit def FutureValueIsAction[T](value: Future[T])(implicit ec: ExecutionContext): Future[ApiData.Action[T]] = value.map(ValueIsAction)
  implicit def FutureFailureIsAction[T](failure: Future[HandlerFailure])(implicit ec: ExecutionContext): Future[ApiData.Action[T]] = failure.map(FailureIsAction)
}
object ApiDsl extends ApiDsl
