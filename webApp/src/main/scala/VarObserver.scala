package wust.webApp

import monix.execution.Ack
import rx._
import monix.reactive.Observer

class VarObserver[T](rx: Var[T]) extends Observer.Sync[T] {
  override def onNext(elem: T): Ack = {
    rx() = elem
    Ack.Continue
  }
  override def onError(ex: Throwable): Unit = throw ex
  override def onComplete(): Unit = ()
}
