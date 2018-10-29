package wust.webApp

import monix.reactive.Observable
import org.scalajs.dom
import org.scalajs.dom.document
import outwatch.dom._
import rx._
import wust.webApp.jsdom.ServiceWorker
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalStateFactory
import wust.webApp.views.{MainView, Rendered}

import scala.scalajs.{LinkingInfo, js}

object Main {
  def domSetup(): Unit = {
    // initialize default-passive-events for smoother scrolling
    defaultPassiveEvents.DefaultPassiveEvents

    // Add polyfill for setImmediate
    // https://developer.mozilla.org/en-US/docs/Web/API/Window/setImmediate
    // Api explanation: https://jphpsf.github.io/setImmediate-shim-demo
    // this will be automatically picked up by monix and used instead of
    // setTimeout( ... , 0)
    // This reduces latency for the async scheduler
    js.Dynamic.global.setImmediate = immediate.immediate
  }

  def main(args: Array[String]): Unit = {
    Logging.setup()

    domSetup()

    val swUpdateIsAvailable = if (!LinkingInfo.developmentMode) ServiceWorker.register() else Observable.empty
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
    val state = GlobalStateFactory.create(swUpdateIsAvailable)

    // in production a css file is generated.
    // TODO: do not depend on css subproject in production?
    DevOnly {
      val styleTag = document.createElement("style")
      document.head.appendChild(styleTag)
      styleTag.innerHTML = wust.css.StyleRendering.renderAll

      // helpers.OutwatchTracing.patch.zipWithIndex.foreach { case (proxy, index) =>
      // org.scalajs.dom.console.log(s"Snabbdom patch ($index)!", proxy)
      // }
    }

    Rendered.init()
    OutWatch.renderReplace("#container", MainView(state)).unsafeRunSync()
  }
}
