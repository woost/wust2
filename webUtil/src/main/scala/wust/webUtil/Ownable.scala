package wust.webUtil

import cats.Monad
import outwatch.dom.Render
import rx._

class Ownable[T](get: Ctx.Owner => T) extends (Ctx.Owner => T) {
  @inline final def apply(ctx: Ctx.Owner): T = get(ctx)
  @inline final def map[R](f: T => R): Ownable[R] = Ownable(get andThen f)
  @inline final def flatMap[R](f: T => Ownable[R]): Ownable[R] = Ownable(ctx => f(get(ctx))(ctx))
}
object Ownable {
  @inline def apply[T](get: Ctx.Owner => T): Ownable[T] = new Ownable[T](get)
  @inline def value[T](get: T): Ownable[T] = new Ownable[T](_ => get)

  implicit object monad extends Monad[Ownable] {
    override def pure[A](x: A): Ownable[A] = Ownable.value(x)
    override def map[A, B](fa: Ownable[A])(f: A => B): Ownable[B] = fa.map(f)
    override def flatMap[A, B](fa: Ownable[A])(f: A => Ownable[B]): Ownable[B] = fa.flatMap(f)
    override def tailRecM[A, B](a: A)(f: A => Ownable[Either[A, B]]): Ownable[B] = f(a).flatMap[B] {
      case Right(r) => Ownable.value(r)
      case Left(r) => tailRecM[A, B](r)(f)
    }
  }

  // IMPORTANT: if you are using this in VNodes without a key, you are potentially fucked. so don't.
  // fucked in the sense of: if the dom element is unmounted and mounted again by snabbdom (which can happen),
  // then the owner is killed and can never be recovered. the element does not react to changes anymore
  implicit def render[T: Render]: Render[Ownable[T]] = ownable => outwatchHelpers.withManualOwner(ownable(_))
}
