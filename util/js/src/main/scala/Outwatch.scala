package wust.util

package object outwatchHelpers {
  implicit class RichVNode(val vNode:outwatch.dom.VNode) {
    def render:org.scalajs.dom.Element = {
      val container = scalatags.JsDom.all.div().render
      outwatch.dom.helpers.DomUtils.render(container, vNode)
      container
    }
  }

  implicit class ObservableRx[T](r:rx.Rx[T])(implicit ctx: rx.Ctx.Owner) {
    def toObservable:rxscalajs.Observable[T] = {
      val subject = rxscalajs.subjects.BehaviorSubject[T](r.now)
      r.foreach(subject.next)
      subject
    }
  }

  implicit def RxVarToSink[T](v:rx.RxVar[T,_])(implicit ctx: rx.Ctx.Owner):outwatch.Sink[T] = {
    val handler = outwatch.dom.createHandler[T]()
    handler(v() = _)
    handler
  }

  // implicit def ObservableToRx[T](obs:Observable[T]):Rx[T] = {
  //   val rx = Var[GraphSelection]()
  //   rx.foreach subject.next
  //   subject
  // }
}
