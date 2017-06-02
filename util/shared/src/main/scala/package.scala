package wust

import scala.concurrent.{ExecutionContext, Future}

package object util {
  implicit class Pipe[T](val v: T) extends AnyVal {
    def |>[U](f: T => U): U = f(v)
    def sideEffect(f: T => Any): T = { f(v); v }
  }

  case class AutoId(start: Int = 0, delta: Int = 1) {
    var localId = start - delta
    def apply() = { localId += delta; localId }
  }

  implicit class RichFuture[A](val fut: Future[A]) extends AnyVal {
    def recoverValue(a: A)(implicit ec: ExecutionContext) = fut.recover { case _ => a }
  }
}
