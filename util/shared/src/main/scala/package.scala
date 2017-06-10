package wust

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Success, Failure }
import scala.util.control.NonFatal
import sourcecode.FullName

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
    def recoverValueWithoutLog(a: A)(implicit ec: ExecutionContext) = fut.recover { case NonFatal(_) => a }
    def recoverValue(a: A)(implicit ec: ExecutionContext, name: sourcecode.FullName) = fut.recover {
      case NonFatal(e) =>
        scribe.error(name)
        scribe.error(e)
        a
    }
    def log(topic: String)(implicit ec: ExecutionContext) = {
      fut.onComplete {
        case Success(res) =>
          scribe.info(topic)
          scribe.info(res)
        case Failure(e) =>
          scribe.error(topic)
          scribe.error(e)
      }
      fut
    }
  }
}
