package wust.webApp

import monix.execution.Ack
import monix.reactive.Observer
import rx._

class VarObserver[T](rx: Var[T]) extends Observer.Sync[T] {
  override def onNext(elem: T): Ack = {
    rx() = elem
    Ack.Continue
  }
  override def onError(ex: Throwable): Unit = throw ex
  override def onComplete(): Unit = ()
}
