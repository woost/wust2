package wust.utilWeb

import org.scalajs.dom.document
import org.scalajs.dom.raw.Element
import cats.effect.IO
import com.raquo.domtypes.generic.keys.Style
import monix.reactive.subjects.PublishSubject
import monix.reactive.{Observable, Observer}
import monix.reactive.OverflowStrategy.Unbounded
import monix.execution.Cancelable
import monix.execution.Ack.Continue
import outwatch.dom.{Attribute, Handler, OutWatch, StringModifier, VDomModifier, VNode}
import outwatch.{AsVDomModifier, ObserverSink, Sink, StaticVNodeRender}
import monix.execution.Scheduler
import monix.execution.ExecutionModel.SynchronousExecution
import outwatch.dom.helpers.{AttributeBuilder, BasicStyleBuilder, EmitterBuilder}
import rx._

import scala.scalajs.js


// Outwatch TODOs:
// when writing: sink <-- obs; obs(action)
// action is always triggered first, even though it is registered after subscribe(<--)
//
// observable.filter does not accept partial functions.filter{case (_,text) => text.nonEmpty}
//

//TODO nicer name
package object outwatchHelpers {
  implicit val monixScheduler: Scheduler = Scheduler.trampoline(executionModel = SynchronousExecution)

  //TODO toObservable/toVar/toRx are methods should be done once and with care. Therefore they should not be in an implicit class on the instance, but in an extra factory like ReactiveConverters.observable/rx/var
  implicit class RichRx[T](rx:Rx[T])(implicit ctx: Ctx.Owner) {
    def toObservable:Observable[T] = Observable.create[T](Unbounded) { observer =>
      rx.foreach(observer.onNext)
      Cancelable() //TODO
    }

    def debug(implicit ctx: Ctx.Owner): Rx[T] = { debug() }
    def debug(name: String = "")(implicit ctx: Ctx.Owner): Rx[T] = {
      rx.foreach(x => println(s"$name: $x"))
      rx
    }
    def debug(print: T => String)(implicit ctx: Ctx.Owner): Rx[T] = {
      rx.foreach(x => println(print(x)))
      rx
    }
  }

  implicit def rxAsVDomModifier[T:StaticVNodeRender](implicit ctx:Ctx.Owner):AsVDomModifier[Rx[T]] = (value: Rx[T]) => value.toObservable
  implicit def rxSeqAsVDomModifier[T:StaticVNodeRender](implicit ctx:Ctx.Owner):AsVDomModifier[Rx[Seq[T]]] = (value: Rx[Seq[T]]) => value.toObservable
  implicit def rxOptionAsVDomModifier[T:StaticVNodeRender](implicit ctx:Ctx.Owner):AsVDomModifier[Rx[Option[T]]] = (value: Rx[Option[T]]) => value.toObservable
  implicit class RichEmitterBuilder[E,O,R](val eb:EmitterBuilder[E,O,R]) extends AnyVal {
    //TODO: scala.rx have a contravariant trait for writing-only
    def -->(sink: Var[_ >: O])(implicit ctx:Ctx.Owner): IO[R] = eb --> sink.toHandler
  }
  implicit class RichAttributeEmitterBuilder[-T, +A <: Attribute](val ab:AttributeBuilder[T,A]) extends AnyVal {
    def <--(valueStream: Rx[T])(implicit ctx:Ctx.Owner) = ab <-- valueStream.toObservable
  }
  implicit class RichStyle[T](val ab:Style[T]) {
    import outwatch.dom.StyleIsBuilder
    //TODO: make outwatch AttributeStreamReceiver public to allow these kinds of builder conversions?
    def <--(valueStream: Rx[T])(implicit ctx:Ctx.Owner) = StyleIsBuilder[T](ab) <-- valueStream.toObservable
  }

  implicit class RichVar[T](rx:Var[T])(implicit ctx: Ctx.Owner) {
    def toHandler: Handler[T] = {
      import cats._, cats.data._, cats.implicits._

      implicit val eqFoo: Eq[T] = Eq.fromUniversalEquals
      val h = Handler.create[T](rx.now).unsafeRunSync().transformSource(_.distinctUntilChanged)
      h.filter(_ != rx.now).foreach(rx.update)
      rx.foreach(h.unsafeOnNext)
      h
    }
  }

  implicit class RichVNode(val vNode: VNode) {
    def render: org.scalajs.dom.Element = {
      val elem = document.createElement("div")
      OutWatch.renderReplace(elem, vNode).unsafeRunSync()
      elem
    }
  }

  implicit class RichHandler[T](val o: Handler[T]) extends AnyVal {
    def toVar(seed: T)(implicit ctx: Ctx.Owner): rx.Var[T] = {
      val rx = Var[T](seed)
      o.foreach(rx.update)
      rx.foreach(o.unsafeOnNext)
      rx
    }
  }

  implicit class RichSink[T](val o: Sink[T]) extends AnyVal {
    def toVar(seed: T)(implicit ctx: Ctx.Owner): rx.Var[T] = {
      val rx = Var[T](seed)
      rx.foreach(o.unsafeOnNext)
      rx
    }
  }

  implicit class RichObservable[T](val o: Observable[T]) extends AnyVal {
    def toRx(seed: T)(implicit ctx: Ctx.Owner): rx.Rx[T] = {
      val rx = Var[T](seed)
      o.foreach(rx() = _)
      rx
    }

    def debug: Cancelable = debug()
    def debug(name: String = "") = o.foreach(x => println(s"$name: $x"))
    def debug(print: T => String) = o.foreach(x => println(print(x)))
  }

  //TODO: Outwatch observable for specific key is pressed Observable[Boolean]
  def keyDown(keyCode: Int):Observable[Boolean] = Observable.merge(
    outwatch.dom.dsl.events.window.onKeyDown.collect { case e if e.keyCode == keyCode => true },
    outwatch.dom.dsl.events.window.onKeyUp.collect { case e if e.keyCode == keyCode => false },
    )
}
