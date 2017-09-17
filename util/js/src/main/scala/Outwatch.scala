package wust.util

import org.scalajs.dom.document

package object outwatchHelpers {
  implicit class RichVNode(val vNode:outwatch.dom.VNode) {
    def render:org.scalajs.dom.Element = {
      val container = document.createElement("div")
      outwatch.dom.helpers.DomUtils.render(container, vNode)
      // import snabbdom._
      // patch(elem, render().asProxy)
      container
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

  implicit def RxVarToSink[T](v:rx.RxVar[T,_]):outwatch.Sink[T] = {
    val handler = outwatch.dom.createHandler[T]()
    handler(v() = _)
    handler
  }
}
