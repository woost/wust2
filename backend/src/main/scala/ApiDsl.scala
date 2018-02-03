package wust.backend

import scala.concurrent.{ExecutionContext, Future}
import wust.api.{ApiEvent, ApiError}, ApiError.HandlerFailure
import cats.implicits._

//TODO move to sloth/mycelium/apidsl project as opionionated dsl?

//TODO better availablity of types not in obj, package object because of type alias? but currently name clash with ApiDsl.Effect/Action => rename
object ApiData {
  type Action[+T] = Either[HandlerFailure, T]
  case class Transformation(state: Option[State], events: Seq[ApiEvent])
  case class Effect[+T](transformation: Transformation, action: Action[T])

  implicit def ActionIsEffect[T](action: Action[T]): Effect[T] = Effect(Transformation(None, Seq.empty), action)

  type MonadError[F[_]] = cats.MonadError[F, HandlerFailure]
  def MonadError[F[_]](implicit m: cats.MonadError[F, HandlerFailure]) = m

  implicit val effectMonadError: MonadError[Effect] = new MonadError[Effect] {
    def pure[A](a: A): Effect[A] = ActionIsEffect(MonadError[Action].pure(a))
    def raiseError[A](e: HandlerFailure): Effect[A] = ActionIsEffect(MonadError[Action].raiseError(e))
    def handleErrorWith[A](e: Effect[A])(f: HandlerFailure => Effect[A]): Effect[A] = e.action.fold(f, _ => e)
    def flatMap[A, B](e: Effect[A])(f: A => Effect[B]): Effect[B] = e.action.fold(raiseError, { res =>
      val effect = f(res)
      val newState = effect.transformation.state orElse e.transformation.state
      val newEvents = e.transformation.events ++ effect.transformation.events
      effect.copy(transformation = Transformation(newState, newEvents))
    })
    def tailRecM[A, B](e: A)(f: A => Effect[Either[A,B]]): Effect[B] = cats.FlatMap[Effect].tailRecM[A,B](e)(f)
  }
}

case class ApiFunction[T](run: Future[State] => ApiFunction.Response[T]) extends AnyVal
object ApiFunction {
  import ApiData._

  case class ReturnValue[T](result: Either[HandlerFailure, T], events: Seq[ApiEvent])
  case class Response[T](state: Future[State], value: Future[ReturnValue[T]])
  object Response {
    def action[T](oldState: Future[State], action: Future[Action[T]])(implicit ec: ExecutionContext): Response[T] = {
      Response(oldState, action.map(action => ReturnValue(action, Seq.empty)))
    }
    def effect[T](oldState: Future[State], effect: Future[Effect[T]])(implicit ec: ExecutionContext): Response[T] = {
      val newState = applyTransformationToState(oldState, effect.map(_.transformation))
      Response(newState, effect.map(e => ReturnValue(e.action, e.transformation.events)))
    }
  }
  trait Factory[F[_]] {
    def apply[T](f: State => Future[F[T]])(implicit ec: ExecutionContext): ApiFunction[T]
    def apply[T](f: => Future[F[T]])(implicit ec: ExecutionContext): ApiFunction[T]
  }

  def redirect[T](api: ApiFunction[T])(f: State => Future[Transformation])(implicit ec: ExecutionContext): ApiFunction[T] = ApiFunction { state =>
    val transformation = state.flatMap(f)
    def newState = applyTransformationToState(state, transformation)
    val response = api.run(newState)
    val newValue = for {
      events <- transformation.map(_.events)
      value <- response.value
    } yield value.copy(events = events ++ value.events)

    response.copy(value = newValue)
  }

  protected def applyTransformationToState(state: Future[State], transformation: Future[Transformation])(implicit ec: ExecutionContext): Future[State] = for {
    t <- transformation
    base <- t.state.fold(state)(Future.successful)
  } yield if (t.events.isEmpty) base else State.applyEvents(base, t.events)

  implicit val apiReturnValueFunctor = cats.derive.functor[ReturnValue]
  implicit def apiResponseFunctor(implicit ec: ExecutionContext) = cats.derive.functor[Response]
  implicit def apiFunctionFunctor(implicit ec: ExecutionContext) = cats.derive.functor[ApiFunction]
}

trait ApiDsl {
  object Action extends ApiFunction.Factory[ApiData.Action] {
    def apply[T](f: State => Future[ApiData.Action[T]])(implicit ec: ExecutionContext): ApiFunction[T] = ApiFunction(s => ApiFunction.Response.action(s, s.flatMap(f)))
    def apply[T](f: => Future[ApiData.Action[T]])(implicit ec: ExecutionContext): ApiFunction[T] = ApiFunction(s => ApiFunction.Response.action(s, f))
  }
  object Effect extends ApiFunction.Factory[ApiData.Effect] {
    def apply[T](f: State => Future[ApiData.Effect[T]])(implicit ec: ExecutionContext): ApiFunction[T] = ApiFunction(s => ApiFunction.Response.effect(s, s.flatMap(f)))
    def apply[T](f: => Future[ApiData.Effect[T]])(implicit ec: ExecutionContext): ApiFunction[T] = ApiFunction(s => ApiFunction.Response.effect(s, f))
  }
  object Returns {
    def raw[T](state: State, result: T, events: Seq[ApiEvent] = Seq.empty): ApiData.Effect[T] = ApiData.Effect(Transforms.raw(state, events), Right(result))
    def apply[T](result: T, events: Seq[ApiEvent]): ApiData.Effect[T] = ApiData.Effect(Transforms(events), Right(result))
    def apply[T](result: T): ApiData.Action[T] = Right(result)

    def error(failure: HandlerFailure, events: Seq[ApiEvent]): ApiData.Effect[Nothing] = ApiData.Effect(Transforms(events), Left(failure))
    def error(failure: HandlerFailure): ApiData.Action[Nothing] = Left(failure)
  }
  object Transforms {
    def raw(state: State, events: Seq[ApiEvent] = Seq.empty): ApiData.Transformation = ApiData.Transformation(Some(state), events)
    def apply(events: Seq[ApiEvent]): ApiData.Transformation = ApiData.Transformation(None, events)
  }


  implicit def ValueIsAction[T](value: T): ApiData.Action[T] = Right(value)
  implicit def ValueIsEffect[T](value: T): ApiData.Effect[T] = ApiData.ActionIsEffect(Right(value))
  implicit def FailureIsAction[T](failure: HandlerFailure): ApiData.Action[T] = Left(failure)
  implicit def FutureValueIsAction[T](value: Future[T])(implicit ec: ExecutionContext): Future[ApiData.Action[T]] = value.map(Right(_))
  implicit def FutureFailureIsAction[T](failure: Future[HandlerFailure])(implicit ec: ExecutionContext): Future[ApiData.Action[T]] = failure.map(Left(_))
}
object ApiDsl extends ApiDsl
