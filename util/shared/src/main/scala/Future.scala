package util
import concurrent.{Future, ExecutionContext}

package object future {
  implicit class RichFuture[T](f: Future[T])(implicit ec: ExecutionContext) {
    def onCompleteRun(code: => Unit): Future[T] = {
      f.onComplete(_ => code)
      f
    }
  }
}
