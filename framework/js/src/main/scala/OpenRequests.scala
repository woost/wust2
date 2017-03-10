package wust.framework

import scala.concurrent.{Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global

import java.util.{Timer, TimerTask}
import org.scalajs.dom.console

import wust.framework.message._
import wust.util.time.StopWatch

case object TimeoutException extends Exception

class OpenRequests[T](timeoutMillis: Int = 60000) {
  import collection.mutable

  private val openRequests = mutable.HashMap.empty[SequenceId, Promise[T]]

  private val nextSeqId: () => SequenceId = {
    var seqId = 0
    () => { seqId += 1; seqId }
  }

  private def newPromise: Promise[T] = {
    val promise = Promise[T]()

    val timer = new Timer
    timer.schedule(new TimerTask {
      def run = promise tryFailure TimeoutException
    }, timeoutMillis)

    promise
  }

  def open(): (SequenceId, Promise[T]) = {
    val stopwatch = StopWatch.started
    val promise = newPromise
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
