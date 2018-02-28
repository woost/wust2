package wust.util

import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.CompositeCancelable
import monix.execution.{Ack, Scheduler}
import monix.reactive._
import monix.reactive.observers.Subscriber
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object BufferWhenTrue {
  /**
    * Buffers for as long as `selector` emits `true`, otherwise for as long as
    * `selector` emits `false` it directly streams any incoming events.
    */
  def apply[A](source: Observable[A], selector: Observable[Boolean])(implicit scheduler: Scheduler): Observable[Seq[A]] =
    Observable.unsafeCreate { out =>
      val conn = CompositeCancelable()
      val subscriber = new SourceSubscriber[A](out, conn)

      conn += source.unsafeSubscribeFn(subscriber)
      conn += selector.unsafeSubscribeFn(new SelectorSubscriber(subscriber))
      conn
    }

  final class SelectorSubscriber(out: SourceSubscriber[_])
    extends Subscriber[Boolean] {

    override val scheduler = out.scheduler
    def onError(ex: Throwable): Unit =
      out.onError(ex)
    def onComplete(): Unit =
      out.onComplete()
    def onNext(elem: Boolean) =
      out.changeBufferState(elem)
  }

  final class SourceSubscriber[A](out: Subscriber[Seq[A]], conn: CompositeCancelable)
    (implicit val scheduler: Scheduler)
    extends Subscriber[A] {

    @volatile var shouldBuffer = false
    private[this] var buffer = ArrayBuffer.empty[A]
    private[this] var isDone = false
    private[this] var lastAck: Future[Ack] = Continue

    def changeBufferState(value: Boolean): Future[Ack] =
      synchronized {
        if (isDone) Stop else {
          shouldBuffer = value
          if (!value && buffer.nonEmpty) {
            val seq = buffer
            buffer = ArrayBuffer.empty
            // Back-pressuring, because call is concurrent
            lastAck = lastAck.syncFlatMap {
              case Continue => out.onNext(seq)
              case Stop => Stop
            }
            lastAck
          } else {
            Continue
          }
        }
      }

    def onNext(elem: A): Future[Ack] =
      synchronized {
        if (isDone) Stop else {
          lastAck = lastAck.syncFlatMap {
            case Stop => Stop
            case Continue =>
              if (shouldBuffer || buffer.nonEmpty) {
                buffer += elem
                if (shouldBuffer) Continue
                else changeBufferState(false)
              } else {
                // Fast path
                out.onNext(Seq(elem))
              }
          }
          // If downstream stops or fails, we cancel everything!
          lastAck.syncOnStopOrFailure(_ => conn.cancel())
        }
      }

    def onError(ex: Throwable): Unit =
      synchronized {
        if (!isDone) {
          isDone = true
          // Back-pressuring, otherwise we violate the protocol!
          lastAck.syncOnContinue(out.onError(ex))
          // Cancel everything, just to be safe
          conn.cancel()
        } else {
          scheduler.reportFailure(ex)
        }
      }

    def onComplete(): Unit =
      synchronized {
        if (!isDone) {
          isDone = true
          // Back-pressuring, otherwise we violate the protocol!
          lastAck.syncOnContinue(out.onComplete())
          // Cancel everything, just to be safe
          conn.cancel()
        }
      }
  }
}
