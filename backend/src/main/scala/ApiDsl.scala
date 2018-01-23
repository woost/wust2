package wust.backend

import scala.concurrent.{ExecutionContext, Future}
import wust.api.{ApiEvent, ApiError}, ApiError.HandlerFailure
import cats.implicits._

//TODO move to sloth/mycelium/apidsl project as opionionated dsl?

//TODO better availablity of types not in obj, package object because of type alias? but currently name clash with ApiDsl.Effect/Action => rename
object ApiData {
  type Action[+T] = Either[HandlerFailure, T]
  case class Transformation(state: Option[State], events: Seq[ApiEvent])
  case class Effect[+T](transformations: Seq[Transformation], action: Action[T])

  implicit def ActionIsEffect[T](action: Action[T]): Effect[T] = Effect(Seq.empty, action)

  implicit val effectMonadError: cats.MonadError[Effect, HandlerFailure] = new cats.MonadError[Effect, HandlerFailure] {
    private val m = cats.MonadError[Action, HandlerFailure]
    def pure[A](a: A): Effect[A] = Effect(Nil, m.pure(a))
    def raiseError[A](e: HandlerFailure): Effect[A] = Effect(Nil, m.raiseError(e))

    def handleErrorWith[A](e: Effect[A])(f: HandlerFailure => Effect[A]): Effect[A] = e.action match {
      case Right(_) => e
      case Left(err) => f(err)
    }
    def flatMap[A, B](e: Effect[A])(f: A => Effect[B]): Effect[B] = e.action match {
      case Right(res) =>
        val effect = f(res)
        effect.copy(transformations = e.transformations ++ effect.transformations)
      case Left(err) => raiseError[B](err)
    }
    //TODO
    def tailRecM[A, B](e: A)(f: A => Effect[Either[A,B]]): Effect[B] = ???
  }
}

case class ApiFunction[T](f: ApiFunction.Arguments => ApiFunction.Response[T]) extends AnyVal {
  import ApiFunction._, ApiData._

  def apply(state: Future[State], combine: ApiFunction.StateEventCombinator)(implicit ec: ExecutionContext): Response[T] = f(Arguments(state, combine))

  //TODO why does cats.syntax.functor not work?
  def map[R](f: T => R)(implicit ec: ExecutionContext): ApiFunction[R] = apiFunctionFunctor.map(this)(f)

  def redirect(f: State => Future[Transformation])(implicit ec: ExecutionContext): ApiFunction[T] = ApiFunction { data =>
    val transformation = data.state.flatMap(f)
    def newState = applyTransformationsToState(data.state, data.combine, transformation.map(_ :: Nil))
    val response = apply(newState, data.combine)
    val newValue = for {
      events <- transformation.map(_.events)
      value <- response.value
    } yield value.copy(events = events ++ value.events)

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
      val newState = applyTransformationsToState(oldState, combine, effect.map(_.transformations))
      Response(newState, effect.map(e => ReturnValue(e.action, e.transformations.flatMap(_.events))))
    }
  }

  protected def applyTransformationsToState(state: Future[State], combine: StateEventCombinator, transformations: Future[Seq[Transformation]])(implicit ec: ExecutionContext): Future[State] = transformations.flatMap { ts =>
    ts.foldRight(state) { (t, state) =>
      val base = t.state.fold(state)(Future.successful)
      if (t.events.isEmpty) base
      else base.map(combine(_, t.events))
    }
  }

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
    def apply[T](state: State, result: T, events: Seq[ApiEvent] = Seq.empty): ApiData.Effect[T] = ApiData.Effect(Transformation(state, events) :: Nil, apply(result))
    def apply[T](result: T, events: Seq[ApiEvent]): ApiData.Effect[T] = ApiData.Effect(Transformation(events) :: Nil, Right(result))
    def apply[T](result: T): ApiData.Action[T] = Right(result)

    def error(failure: HandlerFailure, events: Seq[ApiEvent]): ApiData.Effect[Nothing] = ApiData.Effect(Transformation(events) :: Nil, Left(failure))
    def error(failure: HandlerFailure): ApiData.Action[Nothing] = Left(failure)
  }
  object Transformation {
    def apply(state: State, events: Seq[ApiEvent] = Seq.empty): ApiData.Transformation = ApiData.Transformation(Some(state), events)
    def apply(events: Seq[ApiEvent]): ApiData.Transformation = ApiData.Transformation(None, events)
  }

  implicit def ValueIsAction[T](value: T): ApiData.Action[T] = Returns(value)
  implicit def FailureIsAction[T](failure: HandlerFailure): ApiData.Action[T] = Returns.error(failure)
  implicit def FutureValueIsAction[T](value: Future[T])(implicit ec: ExecutionContext): Future[ApiData.Action[T]] = value.map(ValueIsAction)
  implicit def FutureFailureIsAction[T](failure: Future[HandlerFailure])(implicit ec: ExecutionContext): Future[ApiData.Action[T]] = failure.map(FailureIsAction)
}
object ApiDsl extends ApiDsl
