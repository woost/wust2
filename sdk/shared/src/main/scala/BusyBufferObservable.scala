package wust.sdk

import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.SingleAssignCancelable
import monix.execution.{Ack, Cancelable, CancelableFuture}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success}

final class BusyBufferObservable[+A](source: Observable[A], maxCount: Int) extends Observable[Seq[A]] {

  require(maxCount >= 0, "maxCount must be positive")

  def unsafeSubscribeFn(out: Subscriber[Seq[A]]): Cancelable = source.unsafeSubscribeFn(
      new Subscriber[A] { self =>
        implicit val scheduler = out.scheduler

        // MUST BE synchronized by `self`
        private[this] var cancelable: Cancelable = Cancelable.empty
        // MUST BE synchronized by `self`
        private[this] var ack: Future[Ack] = Continue
        // MUST BE synchronized by `self`
        private[this] var buffer = ListBuffer.empty[A]

        private def sendOut(): Future[Ack] = {
          val oldBuffer = buffer.toList
          buffer = ListBuffer.empty[A]
          out.onNext(oldBuffer)
        }

        def onNext(elem: A): Future[Ack] = self.synchronized {
          cancelable.cancel()
          buffer.append(elem)

          if (maxCount <= buffer.size) {
            ack = sendOut()
            ack
          }
          else {
            ack = ack match {
              case Continue => sendOut()
              case Stop => Stop
              case future if future.isCompleted => future.value.get match {
                case Success(Continue)=> sendOut()
                case Success(Stop) => Stop
                case Failure(ex) =>
                  out.onError(ex)
                  Stop
              }
              case future =>
                var canceled = false
                cancelable = Cancelable(() => canceled = true)
                future.flatMap {
                  case Continue => if (!canceled) self.synchronized(sendOut()) else Continue
                  case Stop => Stop
                }
            }

            Ack.Continue
          }
        }

        def onError(ex: Throwable): Unit = self.synchronized {
          ack = Stop
          buffer = null
          out.onError(ex)
        }

        def onComplete(): Unit = self.synchronized {
          if (buffer.nonEmpty) {
            val bundleToSend = buffer.toList
            // In case the last onNext isn't finished, then
            // we need to apply back-pressure, otherwise this
            // onNext will break the contract.
            ack.syncOnContinue {
              out.onNext(bundleToSend)
              out.onComplete()
            }
          } else {
            // We can just stream directly
            out.onComplete()
          }

          // GC relief
          buffer = null
          // Ensuring that nothing else happens
          ack = Stop
        }
      }
    )
}

