package wust.webUtil.outwatchHelpers

import monix.execution.{Ack, Cancelable}
import monix.reactive.{Observable, Observer}
import outwatch.dom._
import outwatch.dom.helpers.EmitterBuilder
import rx._

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

// TODO: outwatch: easily switch classes on and off via Boolean or Rx[Boolean]
//TODO: outwatch: onInput.target foreach { elem => ... }
//TODO: outwatch: Emitterbuilder.timeOut or delay

class CombinedObserver[T](observers: Seq[Observer[T]])(implicit ec: ExecutionContext) extends Observer[T] {
  def onError(ex: Throwable): Unit = observers.foreach(_.onError(ex))
  def onComplete(): Unit = observers.foreach(_.onComplete())
  def onNext(elem: T): scala.concurrent.Future[monix.execution.Ack] = {
    Future.sequence(observers.map(_.onNext(elem))).map { acks =>
      val stops = acks.collect { case Ack.Stop => Ack.Stop }
      stops.headOption getOrElse Ack.Continue
    }
  }
}


@inline class ModifierBooleanOps(val condition: Boolean) extends AnyVal {
  @inline def apply(m: => VDomModifier):VDomModifier = if(condition) VDomModifier(m) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier, m4: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3,m4) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier, m4: => VDomModifier, m5: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3,m4,m5) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier, m4: => VDomModifier, m5: => VDomModifier, m6: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3,m4,m5,m6) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier, m4: => VDomModifier, m5: => VDomModifier, m6: => VDomModifier, m7: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3,m4,m5,m6,m7) else VDomModifier.empty
}

