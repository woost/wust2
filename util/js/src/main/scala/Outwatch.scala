package wust.util

import org.scalajs.dom.document
import org.scalajs.dom.raw.Element
import cats.effect.IO
import monix.reactive.subjects.PublishSubject
import monix.reactive.{Observable, Observer}
import monix.reactive.OverflowStrategy.Unbounded
import monix.execution.Cancelable
import monix.execution.Ack.Continue
import monix.execution.Scheduler.Implicits.global
import outwatch.dom.{Handler, VNode}
import outwatch.{ObserverSink, Sink}
import rx._

import scala.scalajs.js


// Outwatch TODOs:
// when writing: sink <-- obs; obs(action)
// action is always triggered first, even though it is registered after subscribe(<--)
//
// observable.filter does not accept partial functions.filter{case (_,text) => text.nonEmpty}
//
// like Handler, Subject needs to be wrapped in IO
//
// handler[A].map(A => B) should return Sink[A] with Observable[B]


package object outwatchHelpers {

  implicit class RichRx[T](rx:Rx[T])(implicit ctx: Ctx.Owner) {
    def toObservable:Observable[T] = Observable.create[T](Unbounded) { observer =>
      rx.foreach(observer.onNext)
      Cancelable() //TODO
    }.startWith(Seq(rx.now))

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

      def unsafeSink(sink: Sink[T]): ObserverSink[T] = {
        val subject = PublishSubject[T]
        val newSink = ObserverSink(subject)

        (sink <-- subject).unsafeRunSync()
        newSink
      }

      val h = Handler.create[T](rx.now).unsafeRunSync()
      val sink = unsafeSink(h)
      implicit val eqFoo: Eq[T] = Eq.fromUniversalEquals
      val hDistinct = h.distinctUntilChanged
      hDistinct.foreach(rx.update)
      rx.foreach(sink.observer.onNext)

      h
    }
  }

//     implicit class RxSubjectBehavior[T](obs:subjects.BehaviorSubject[T])(implicit ctx: rx.Ctx.Owner) {
//     def toRx:rx.Rx[T] = {
//       val rx = Var[T](obs.value)
//       obs(rx() = _)
//       rx
//     }
//   }


  implicit class RichVNode(val vNode:VNode) {
//    def render:org.scalajs.dom.Element = {
//      val elem = document.createElement("div")
//      outwatch.dom.helpers.DomUtils.render(elem, vNode).unsafeRunSync
//      // import snabbdom._
//      // patch(elem, vNode.value.asProxy.unsafeRunSync)
//      elem
//    }
  }

  // implicit class RichVNodeIO(val vNode:IO[VNode]) {
  //   def render:org.scalajs.dom.Element = {
  //     val elem = document.createElement("div")
  //     vNode.flatMap(vNode => outwatch.dom.helpers.DomUtils.render(elem, vNode)).unsafeRunSync
  //     elem
  //   }
  // }


//  implicit class RichHandler[T](val o:Handler[T]) extends AnyVal {
//    def toVar(seed:T)(implicit ctx: Ctx.Owner):rx.Rx[T] = {
//      val rx = Var[T](seed)
//      o(rx() = _)
//      rx
//    }

  implicit class RichObservable[T](val o:Observable[T]) extends AnyVal {
    def toRx(seed:T)(implicit ctx: Ctx.Owner):rx.Rx[T] = {
      val rx = Var[T](seed)
      o.foreach(rx() = _)
      rx
    }

    //TODO: still needed with monix?
    def replaceWithLatestFrom[R](o2:Observable[R]):Observable[R] = {
      o.withLatestFrom(o2)((_,second) => second)
    }

    // def bufferUnless(predicates: Observable[Boolean]):Observable[List[T]] = {
    //   val truePredicates = predicates.filter(_ == true)
    //   val (trueEvents, falseEvents) = o.withLatestFrom(predicates).partition{ case (_, predicate) => predicate }
    //   val toBeBufferedEvents = falseEvents.map { case (e,_) => e }
    //   val bufferedEvents = toBeBufferedEvents.bufferWhen(() => truePredicates)
    //   val flushEvents = trueEvents.map { case (x, _) => List(x) }
    //   flushEvents merge bufferedEvents
    // }

    // def combineLatestWith[A, B, C, D, R](a: Observable[A], b: Observable[B], c: Observable[C], d: Observable[D])(f: (T, A, B, C, D) => R): Observable[R] = {
    //   val combined = o.combineLatestWith[A, B, C, (T, A, B, C)](a, b, c)((o, a, b, c) => (o, a, b, c))
    //   combined.combineLatestWith[D, R](d) { case ((o, a, b, c), d) => f(o, a, b, c, d) }
    // }

    def debug:Observable[T] = { debug() }
    def debug(name: String = ""): Observable[T] = {
      o.foreach(x => println(s"$name: $x"))
      o
    }
    def debug(print: T => String): Observable[T] = {
      o.foreach(x => println(print(x)))
      o
    }
  }

  implicit def FuncToSink[T,R](f:T => R):outwatch.Sink[T] = {
    //TODO: outwatch: accept function => Any or R
    outwatch.Sink.create[T](e => {f(e); IO{(); Continue}})
  }

  implicit def ElementFuncToSink2[R](f:Element => R):outwatch.Sink[(Element,Element)] = {
    //TODO: outwatch: accept function => Any or R
    outwatch.Sink.create[(Element,Element)]{case (_, after) => f(after); IO{(); Continue} }
  }
}
