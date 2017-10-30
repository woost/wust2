package wust.util

import org.scalajs.dom.document
import org.scalajs.dom.raw.Element
import cats.effect.IO

package object outwatchHelpers {
  implicit class RichVNode(val vNode:outwatch.dom.VNode) {
    def render:org.scalajs.dom.Element = {
      val elem = document.createElement("div")
      outwatch.dom.helpers.DomUtils.render(elem, vNode).unsafeRunSync
      // import snabbdom._
      // patch(elem, vNode.value.asProxy.unsafeRunSync)
      elem
    }
  }

  implicit class RichVNodeIO(val vNode:IO[outwatch.dom.VNode]) {
    def render:org.scalajs.dom.Element = {
      val elem = document.createElement("div")
      vNode.flatMap(vNode => outwatch.dom.helpers.DomUtils.render(elem, vNode)).unsafeRunSync
      elem
    }
  }

  implicit class ObservableRx[T](r:rx.Rx[T])(implicit ctx: rx.Ctx.Owner) {
    def toObservable:rxscalajs.Observable[T] = rxscalajs.Observable.create { observer =>
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

  implicit def RxVarToSink[T](v:rx.RxVar[T,_]):IO[outwatch.dom.Handler[T]] = {
    for{
      handler <- outwatch.dom.createHandler[T]()
      _ <- IO {handler(v() = _)}
    } yield handler
  }

  implicit def FuncToSink[T,R](f:T => R):outwatch.Sink[T] = {
    //TODO: outwatch: accept function => Any or R
    outwatch.Sink.create[T](e => {f(e); IO{()}})
  }

  implicit def ElementFuncToSink2[R](f:Element => R):outwatch.Sink[(Element,Element)] = {
    //TODO: outwatch: accept function => Any or R
    outwatch.Sink.create[(Element,Element)]{case (_, after) => {f(after); IO{()}}}
  }
}
