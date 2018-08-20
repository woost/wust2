package wust.webApp

trait RequestAnimationFrameMock {
  import scala.collection.mutable
  import scala.scalajs.js
  import org.scalajs.dom

  if(js.isUndefined(js.Dynamic.global.requestAnimationFrame))
    js.Dynamic.global.updateDynamic("requestAnimationFrame") { cb: js.Function0[Any] =>
      dom.window.setTimeout(cb, 0)
    }
}
