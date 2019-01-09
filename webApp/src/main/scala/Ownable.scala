package wust.webApp

import rx._
import cats.Monad
import outwatch.AsVDomModifier

class Ownable[T](val get: Ctx.Owner => T) {
  @inline final def map[R](f: T => R): Ownable[R] = Ownable(get andThen f)
  @inline final def mapWithOwner[R](f: Ctx.Owner => T => R): Ownable[R] = Ownable(ctx => f(ctx)(get(ctx)))
  @inline final def flatMap[R](f: T => Ownable[R]): Ownable[R] = Ownable(ctx => f(get(ctx)).get(ctx))
  @inline final def flatMapWithOwner[R](f: Ctx.Owner => T => Ownable[R]): Ownable[R] = Ownable(ctx => f(ctx)(get(ctx)).get(ctx))
}
object Ownable {
  @inline def apply[T](get: Ctx.Owner => T): Ownable[T] = new Ownable[T](get)
  @inline def value[T](get: T): Ownable[T] = new Ownable[T](_ => get)

  implicit val monad: Monad[Ownable] = new Monad[Ownable] {
    override def pure[A](x: A): Ownable[A] = Ownable.value(x)
    override def map[A, B](fa: Ownable[A])(f: A => B): Ownable[B] = fa.map(f)
    override def flatMap[A, B](fa: Ownable[A])(f: A => Ownable[B]): Ownable[B] = fa.flatMap(f)
    override def tailRecM[A, B](a: A)(f: A => Ownable[Either[A, B]]): Ownable[B] = f(a).flatMapWithOwner[B] { owner => v =>
      f(a).get(owner) match {
        case Right(r) => Ownable.value(r)
        case Left(r) => tailRecM[A, B](r)(f)
      }
    }
  }

  implicit def asVDomModifier[T: AsVDomModifier]: AsVDomModifier[Ownable[T]] = ownable => outwatchHelpers.withManualOwner(ownable.get(_))
}
