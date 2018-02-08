package wust.util

import org.scalajs.dom.document
import org.scalajs.dom.raw.Element
import cats.effect.IO
import monix.reactive.subjects.PublishSubject
import monix.reactive.{Observable, Observer}
import monix.reactive.OverflowStrategy.Unbounded
import monix.execution.Cancelable
import monix.execution.Ack.Continue
import outwatch.dom.{Handler, OutWatch, VNode}
import outwatch.{ObserverSink, Sink}
import monix.execution.Scheduler
import monix.execution.ExecutionModel.SynchronousExecution
import rx._

import scala.scalajs.js


// Outwatch TODOs:
// when writing: sink <-- obs; obs(action)
// action is always triggered first, even though it is registered after subscribe(<--)
//
// observable.filter does not accept partial functions.filter{case (_,text) => text.nonEmpty}
//

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

  implicit class RichVar[T](rx:Var[T])(implicit ctx: Ctx.Owner) {
    def toHandler: Handler[T] = {
      import cats._, cats.data._, cats.implicits._

      implicit val eqFoo: Eq[T] = Eq.fromUniversalEquals
      val h = Handler.create[T](rx.now).unsafeRunSync().transformSource(_.distinctUntilChanged)
      h.filter(_ != rx.now).foreach(rx.update)
      rx.foreach(h.unsafeOnNext _)
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
}
