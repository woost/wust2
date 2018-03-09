package wust

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}
import scala.util.Try

package object util {
  implicit class Pipe[T](val v: T) extends AnyVal {
    def |>[U](f: T => U): U = f(v)
    def sideEffect(f: T => Any): T = { f(v); v }
  }

  implicit class RichFuture[T](val fut: Future[T]) extends AnyVal {
    def recoverValueWithoutLog(a: T)(implicit ec: ExecutionContext) = fut.recover { case NonFatal(_) => a }
    def recoverValue(a: T)(implicit ec: ExecutionContext, name: sourcecode.FullName, line: sourcecode.Line) = fut.recover {
      case NonFatal(e) =>
        scribe.error(s"${name.value}:${line}")
        scribe.error(e)
        a
    }
    def log(customMessage: String = null)(implicit ec: ExecutionContext, name: sourcecode.FullName, line: sourcecode.Line): Future[T] = {
      val message = Option(customMessage).fold("")(" - " + _)
      fut.onComplete {
        case Success(res) =>
          scribe.info(s"${name.value}:${line.value}$message - $res")
        case Failure(e) =>
          scribe.error(s"${name.value}:${line.value}$message", e)
      }
      fut
    }

    // Polyfill for 2.11
    // from https://github.com/scala/scala/blob/79e5101738d5b4675ab4194d6c18d443a496ef7c/src/library/scala/concurrent/impl/Promise.scala#L27
    def transform[S](f: Try[T] => Try[S])(implicit executor: ExecutionContext): Future[S] = {
      val p = Promise[S]()
      fut.onComplete { result => p.complete(try f(result) catch { case NonFatal(t) => Failure(t) }) }
      p.future
    }
  }
}
