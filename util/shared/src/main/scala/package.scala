package wust

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}
import scala.util.Try

package object util {

  implicit class RichFuture[T](val fut: Future[T]) extends AnyVal {
    def recoverValueWithoutLog(a: T)(implicit ec: ExecutionContext) = fut.recover {
      case NonFatal(_) => a
    }
    def recoverValue(a: T)(implicit ec: ExecutionContext, name: sourcecode.FullName, line: sourcecode.Line) =
      fut.recover {
        case NonFatal(e) =>
          scribe.error(s"${name.value}:${line.value}", e)
          a
      }
    def log(customMessage: String = null)(
        implicit ec: ExecutionContext,
        name: sourcecode.FullName,
        line: sourcecode.Line
    ): Future[T] = {
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
      fut.onComplete { result =>
        p.complete(
          try f(result)
          catch { case NonFatal(t) => Failure(t) }
        )
      }
      p.future
    }
  }

  implicit class RichBoolean(val b: Boolean) extends AnyVal {
    @inline final def ifTrueSeq[A](a: => Seq[A]): Seq[A] = ifTrue(a)
    @inline final def ifFalseSeq[A](a: => Seq[A]): Seq[A] = ifFalse(a)
    @inline final def ifTrueSet[A](a: => Set[A]): Set[A] = ifTrue(a)
    @inline final def ifFalseSet[A](a: => Set[A]): Set[A] = ifFalse(a)
    @inline final def ifTrueOption[A](a: => A): Option[A] = ifTrue(Option(a))
    @inline final def ifFalseOption[A](a: => A): Option[A] = ifFalse(Option(a))

    @inline final def ifTrue[A](a: => A)(implicit e: Empty[A]): A = if (b) a else e.empty
    @inline final def ifFalse[A](a: => A)(implicit e: Empty[A]): A = if (!b) a else e.empty
  }
}
