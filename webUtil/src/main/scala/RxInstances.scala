package wust.webUtil

import rx._
import cats.Monad

trait RxInstances {
  implicit def monad(implicit ctx: Ctx.Owner): Monad[Rx] = new Monad[Rx] {
    override def pure[A](x: A): Rx[A] = Var(x)
    override def map[A, B](fa: Rx[A])(f: A => B): Rx[B] = fa.map(f)
    override def flatMap[A, B](fa: Rx[A])(f: A => Rx[B]): Rx[B] = fa.flatMap(f)
    override def tailRecM[A, B](a: A)(f: A => Rx[Either[A, B]]): Rx[B] = f(a).flatMap {
      case Right(b) => Var(b)
      case Left(a) => tailRecM(a)(f)
    }
  }
}
object RxInstances extends RxInstances
