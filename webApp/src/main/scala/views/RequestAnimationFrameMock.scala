package wust.webApp

trait RequestAnimationFrameMock {
  import org.scalajs.dom

  import scala.scalajs.js

  if(js.isUndefined(js.Dynamic.global.requestAnimationFrame))
    js.Dynamic.global.updateDynamic("requestAnimationFrame") { cb: js.Function0[Any] =>
      dom.window.setTimeout(cb, 0)
    }
}
