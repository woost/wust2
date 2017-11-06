package wust.util

import org.scalajs.dom.document
import org.scalajs.dom.raw.Element
import cats.effect.IO
import rxscalajs.{Observable, Observer}
import outwatch.dom.{Handler, VNode}
import outwatch.Sink

import scala.scalajs.js

package object outwatchHelpers {
  implicit class RichVNode(val vNode:VNode) {
//    def render:org.scalajs.dom.Element = {
//      val elem = document.createElement("div")
//      outwatch.dom.helpers.DomUtils.render(elem, vNode).unsafeRunSync
//      // import snabbdom._
//      // patch(elem, vNode.value.asProxy.unsafeRunSync)
//      elem
//    }
  }

  implicit class RichVNodeIO(val vNode:IO[VNode]) {
    def render:org.scalajs.dom.Element = {
      val elem = document.createElement("div")
      vNode.flatMap(vNode => outwatch.dom.helpers.DomUtils.render(elem, vNode)).unsafeRunSync
      elem
    }
  }

  implicit class Richobservable[T](val o:Observable[T]) extends AnyVal {
    def replaceWithLatest[R](o2:Observable[R]):Observable[R] = {
      o.combineLatestWith[R,R](o2)((_, text) => text)
    }

    def bufferUnless(predicates: Observable[Boolean]):Observable[List[T]] = {
      val truePredicates = predicates.filter(_ == true)
      val (trueEvents, falseEvents) = o.withLatestFrom(predicates).partition{ case (_, predicate) => predicate }
      val toBeBufferedEvents = falseEvents.map { case (e,_) => e }
      val bufferedEvents = toBeBufferedEvents.bufferWhen(() => truePredicates)
      val flushEvents = trueEvents.map { case (x, _) => List(x) }
      flushEvents merge bufferedEvents
    }

    def combineLatestWith[A, B, C, D, R](a: Observable[A], b: Observable[B], c: Observable[C], d: Observable[D])(f: (T, A, B, C, D) => R): Observable[R] = {
      val combined = o.combineLatestWith[A, B, C, (T, A, B, C)](a, b, c)((o, a, b, c) => (o, a, b, c))
      combined.combineLatestWith[D, R](d) { case ((o, a, b, c), d) => f(o, a, b, c, d) }
    }

    def debug:Observable[T] = { debug() }
    def debug(name: String = "") = {
      o(x => println(s"$name: $x"))
      o
    }
    def debug(print: T => String) = {
      o(x => println(print(x)))
      o
    }
  }

  implicit def FuncToSink[T,R](f:T => R):outwatch.Sink[T] = {
    //TODO: outwatch: accept function => Any or R
    outwatch.Sink.create[T](e => {f(e); IO{()}})
  }

  implicit def unwrapIOHandler[T](h:IO[Handler[T]]):Handler[T] = h.unsafeRunSync()

  implicit def ElementFuncToSink2[R](f:Element => R):outwatch.Sink[(Element,Element)] = {
    //TODO: outwatch: accept function => Any or R
    outwatch.Sink.create[(Element,Element)]{case (_, after) => {f(after); IO{()}}}
  }
}
