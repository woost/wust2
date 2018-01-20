package wust.backend

import scala.concurrent.{ExecutionContext, Future}
import wust.api.{ApiEvent, ApiError}, ApiError.HandlerFailure
import cats.implicits._

//TODO move to sloth/mycelium/apidsl project as opionionated dsl?

object ApiData {
  case class Action[+T](result: Either[HandlerFailure, T]) extends AnyVal
  case class Effect[+T](events: Seq[ApiEvent], action: Action[T])

  implicit val apiActionFunctor = cats.derive.functor[Action]
  implicit val apiEffectFunctor = cats.derive.functor[Effect]

  //TODO: why do we need these Future[F[T]] types and implicits?
  type FutureAction[T] = Future[Action[T]]
  type FutureEffect[T] = Future[Effect[T]]
  implicit def futureApiActionFunctor(implicit ec: ExecutionContext) = cats.derive.functor[FutureAction]
  implicit def futureApiEffectFunctor(implicit ec: ExecutionContext) = cats.derive.functor[FutureEffect]
}

sealed trait ApiFunction[T] extends Any {
  def apply(state: Future[State], combine: (State, Seq[ApiEvent]) => State)(implicit ec: ExecutionContext): ApiFunction.ReturnValue[T]
  def redirectWithEvents(eventsf: State => Future[Seq[ApiEvent]]) = ApiFunction.RedirectWithEvents(this, eventsf)
}
object ApiFunction {
  case class Response[T](result: Either[HandlerFailure, T], events: Seq[ApiEvent])
  case class ReturnValue[T](state: Future[State], response: Future[Response[T]])
  object ReturnValue {
    def apply[T](oldState: Future[State], action: Future[ApiData.Action[T]])(implicit ec: ExecutionContext): ReturnValue[T] = {
      ReturnValue(oldState, action.map(action => Response(action.result, Seq.empty)))
    }
    def apply[T](oldState: Future[State], combine: (State, Seq[ApiEvent]) => State, effect: Future[ApiData.Effect[T]])(implicit ec: ExecutionContext): ReturnValue[T] = {
      val r = for {
        oldState <- oldState
        effect <- effect
        newState = if (effect.events.isEmpty) oldState else combine(oldState, effect.events)
      } yield (newState, effect.action.result, effect.events)

      ReturnValue(r.map(_._1), r.map(r => Response(r._2, r._3)))
    }
  }

  case class Action[T](f: State => Future[ApiData.Action[T]]) extends AnyVal with ApiFunction[T] {
    def apply(state: Future[State], combine: (State, Seq[ApiEvent]) => State)(implicit ec: ExecutionContext) = ReturnValue(state, state.flatMap(f))
  }
  case class IndependentAction[T](f: () => Future[ApiData.Action[T]]) extends AnyVal with ApiFunction[T] {
    def apply(state: Future[State], combine: (State, Seq[ApiEvent]) => State)(implicit ec: ExecutionContext) = ReturnValue(state, f())
  }
  case class Effect[T](f: State => Future[ApiData.Effect[T]]) extends AnyVal with ApiFunction[T] {
    def apply(state: Future[State], combine: (State, Seq[ApiEvent]) => State)(implicit ec: ExecutionContext) = ReturnValue(state, combine, state.flatMap(f))
  }
  case class RedirectWithEvents[T](f: ApiFunction[T], eventsf: State => Future[Seq[ApiEvent]]) extends ApiFunction[T] {
    def apply(state: Future[State], combine: (State, Seq[ApiEvent]) => State)(implicit ec: ExecutionContext) = {
      val events = state.flatMap(eventsf)
      val newState = for {
        state <- state
        events <- events
      } yield if (events.isEmpty) state else combine(state, events)

      val value = f(newState, combine)
      val newResponse = for {
        events <- events
        response <- value.response
      } yield response.copy(events = events ++ response.events)

      value.copy(response = newResponse)
    }
  }

  implicit def apiFunctionFunctor(implicit ec: ExecutionContext) = cats.derive.functor[ApiFunction]
}

trait ApiDsl {
  sealed trait ApiFunctionFactory[F[_]] {
    def apply[T](f: State => Future[F[T]]): ApiFunction[T]
  }
  object Action extends ApiFunctionFactory[ApiData.Action] {
    def apply[T](f: State => Future[ApiData.Action[T]]): ApiFunction[T] = ApiFunction.Action(f)
    def apply[T](f: => Future[ApiData.Action[T]]): ApiFunction[T] = ApiFunction.IndependentAction(() => f)
  }
  object Effect extends ApiFunctionFactory[ApiData.Effect] {
    def apply[T](f: State => Future[ApiData.Effect[T]]): ApiFunction[T] = ApiFunction.Effect(f)
  }
  object Returns {
    def apply[T](result: T, events: Seq[ApiEvent] = Seq.empty): ApiData.Effect[T] = ApiData.Effect(events, apply(result))
    def apply[T](result: T): ApiData.Action[T] = ApiData.Action(Right(result))

    def error(failure: HandlerFailure, events: Seq[ApiEvent] = Seq.empty): ApiData.Effect[Nothing] = ApiData.Effect(events, error(failure))
    def error(failure: HandlerFailure): ApiData.Action[Nothing] = ApiData.Action(Left(failure))
  }

  implicit def ValueIsAction[T](value: T): ApiData.Action[T] = Returns(value)
  implicit def FailureIsAction[T](failure: HandlerFailure): ApiData.Action[T] = Returns.error(failure)
  implicit def FutureValueIsAction[T](value: Future[T])(implicit ec: ExecutionContext): Future[ApiData.Action[T]] = value.map(ValueIsAction)
  implicit def FutureFailureIsAction[T](failure: Future[HandlerFailure])(implicit ec: ExecutionContext): Future[ApiData.Action[T]] = failure.map(FailureIsAction)
}
object ApiDsl extends ApiDsl
