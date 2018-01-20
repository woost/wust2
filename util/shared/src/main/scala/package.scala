package wust

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

package object util {
  implicit class Pipe[T](val v: T) extends AnyVal {
    def |>[U](f: T => U): U = f(v)
    def sideEffect(f: T => Any): T = { f(v); v }
  }

  implicit class RichFuture[A](val fut: Future[A]) extends AnyVal {
    def recoverValueWithoutLog(a: A)(implicit ec: ExecutionContext) = fut.recover { case NonFatal(_) => a }
    def recoverValue(a: A)(implicit ec: ExecutionContext, name: sourcecode.FullName, line: sourcecode.Line) = fut.recover {
      case NonFatal(e) =>
        scribe.error(s"${name.value}:${line}")
        scribe.error(e)
        a
    }
    def log(customMessage: String = null)(implicit ec: ExecutionContext, name: sourcecode.FullName, line: sourcecode.Line): Future[A] = {
      val message = Option(customMessage).fold("")(" - " + _)
      fut.onComplete {
        case Success(res) =>
          scribe.info(s"${name.value}:${line.value}$message")
          scribe.info(res)
        case Failure(e) =>
          scribe.error(s"${name.value}:${line.value}$message")
          scribe.error(e)
      }
      fut
    }
  }
}
