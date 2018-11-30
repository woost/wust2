package wust.serviceUtil

import monix.eval.Task

import scala.concurrent.duration.FiniteDuration

object MonixUtils {
  def retryWithBackoff[A](source: Task[A], maxRetries: Int, initialDelay: FiniteDuration): Task[A] = source
    .onErrorHandleWith { case ex: Exception =>
      if (maxRetries > 0) retryWithBackoff(source, maxRetries - 1, initialDelay * 2).delayExecution(initialDelay)
      else Task.raiseError(ex)
    }
}
