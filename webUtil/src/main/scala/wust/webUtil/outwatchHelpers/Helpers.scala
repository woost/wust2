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
trait RxEmitterBuilderBase[+O,+R] extends EmitterBuilder[O, R] { self =>
  @inline def transformRx[T](tr: Ctx.Owner => Rx[O] => Rx[T]): EmitterBuilder[T, R]
  @inline def map[T](f: O => T): EmitterBuilder[T, R] = transformRx[T](implicit ctx => _.map(f))
  @inline def filter(predicate: O => Boolean): EmitterBuilder[O, R] = transformRx[O](implicit ctx => _.filter(predicate))
  @inline def collect[T](f: PartialFunction[O, T]): EmitterBuilder[T, R] = mapOption(f.lift)
  @inline def mapOption[T](f: O => Option[T]): EmitterBuilder[T, R] = transformRx[T](implicit ctx => v => v.map(v => f(v)).filter(_.isEmpty).map(_.get))

  def mapResult[S](f: R => S): EmitterBuilder[O, S] = new RxEmitterBuilderBase[O, S] {
    @inline def transform[T](tr: Observable[O] => Observable[T]): EmitterBuilder[T, S] = self.transform(tr).mapResult(f)
    @inline def transformRx[T](tr: Ctx.Owner => Rx[O] => Rx[T]): EmitterBuilder[T, S] = self.transformRx(tr).mapResult(f)
    @inline def -->(observer: Observer[O]): S = f(self --> observer)
  }
}

class RxTransformingEmitterBuilder[E,O](rx: Rx[E], transformer: Ctx.Owner => Rx[E] => Rx[O]) extends RxEmitterBuilderBase[O, VDomModifier] {
  @inline override def transform[T](tr: Observable[O] => Observable[T]): EmitterBuilder[T, VDomModifier] = EmitterBuilder.fromObservable[T](tr(rx.toObservable(transformer)))
  @inline def transformRx[T](tr: Ctx.Owner => Rx[O] => Rx[T]): EmitterBuilder[T, VDomModifier] = new RxTransformingEmitterBuilder[E,T](rx, ctx => rx => tr(ctx)(transformer(ctx)(rx)))
  override def -->(observer: Observer[O]): VDomModifier = {
    outwatch.dom.managed { () =>
      implicit val ctx = createManualOwner()
      transformer(ctx)(rx).foreach(observer.onNext)(ctx)
      Cancelable(() => ctx.contextualRx.kill())
    }
  }
}

class RxEmitterBuilder[O](rx: Rx[O]) extends RxEmitterBuilderBase[O, VDomModifier] {
  @inline override def transform[T](tr: Observable[O] => Observable[T]): EmitterBuilder[T, VDomModifier] = EmitterBuilder.fromObservable(tr(rx.toObservable))
  @inline def transformRx[T](tr: Ctx.Owner => Rx[O] => Rx[T]): EmitterBuilder[T, VDomModifier] = new RxTransformingEmitterBuilder(rx, tr)
  override def -->(observer: Observer[O]): VDomModifier = {
    outwatch.dom.managed { () =>
      implicit val ctx = Ctx.Owner.Unsafe
      val obs = rx.foreach(observer.onNext)
      Cancelable(() => obs.kill())
    }
  }
}
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


@inline class ModifierBooleanOps(condition: Boolean) {
  @inline def apply(m: => VDomModifier):VDomModifier = if(condition) VDomModifier(m) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier, m4: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3,m4) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier, m4: => VDomModifier, m5: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3,m4,m5) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier, m4: => VDomModifier, m5: => VDomModifier, m6: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3,m4,m5,m6) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier, m4: => VDomModifier, m5: => VDomModifier, m6: => VDomModifier, m7: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3,m4,m5,m6,m7) else VDomModifier.empty
}

