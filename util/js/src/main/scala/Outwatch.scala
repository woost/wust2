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
    def render:org.scalajs.dom.Element = {
      val elem = document.createElement("div")
      outwatch.dom.helpers.DomUtils.render(elem, vNode).unsafeRunSync
      // import snabbdom._
      // patch(elem, vNode.value.asProxy.unsafeRunSync)
      elem
    }
  }

  implicit class RichVNodeIO(val vNode:IO[VNode]) {
    def render:org.scalajs.dom.Element = {
      val elem = document.createElement("div")
      vNode.flatMap(vNode => outwatch.dom.helpers.DomUtils.render(elem, vNode)).unsafeRunSync
      elem
    }
  }

  implicit class ObservableRx[T](r:rx.Rx[T])(implicit ctx: rx.Ctx.Owner) {
    def toObservable:Observable[T] = Observable.create { observer =>
      r.foreach(observer.next)
      ()
    }
  }

  // implicit class RxSubjectBehavior[T](obs:rxscalajs.subjects.BehaviorSubject[T])(implicit ctx: rx.Ctx.Owner) {
  //   def toRx:rx.Rx[T] = {
  //     val rx = Var[T](obs.value)
  //     obs(rx() = _)
  //     rx
  //   }
  // }

  implicit def RxVarToSink[T](v:rx.RxVar[T,_]):Handler[T] = {
    val handler = outwatch.dom.createHandler[T]().unsafeRunSync
    handler(v() = _)
    handler
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
