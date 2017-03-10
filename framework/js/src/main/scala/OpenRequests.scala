package wust.framework

import scala.concurrent.{Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalajs.dom.console

import wust.framework.message._
import wust.util.time.StopWatch

case object TimeoutException extends Exception

object TimeoutPromise {
  import scala.scalajs.js.timers._

  def apply[T](timeoutMillis: Int): Promise[T] = {
    val promise = Promise[T]()

    val timeout = setTimeout(timeoutMillis)(promise tryFailure TimeoutException)
    promise.future.onComplete(_ => clearTimeout(timeout))

    promise
  }
}

class OpenRequests[T](timeoutMillis: Int = 60000) {
  import collection.mutable

  private val openRequests = mutable.HashMap.empty[SequenceId, Promise[T]]

  private val nextSeqId: () => SequenceId = {
    var seqId = 0
    () => { seqId += 1; seqId }
  }

  def open(): (SequenceId, Promise[T]) = {
    val stopwatch = StopWatch.started
    val promise = TimeoutPromise[T](timeoutMillis)
    val seqId = nextSeqId()
    openRequests += seqId -> promise
    promise.future onComplete { _ =>
      openRequests -= seqId
      console.log(s"Request $seqId: ${stopwatch.readMillis}ms")
    }
    seqId -> promise
  }

  def get(seqId: SequenceId): Option[Promise[T]] = openRequests.get(seqId)
}
