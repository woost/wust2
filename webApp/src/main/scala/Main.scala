package wust.webApp

import monix.reactive.Observable
import outwatch.dom._
import rx._
import wust.webApp.outwatchHelpers._
import org.scalajs.dom.{console, document}

import scala.scalajs.{LinkingInfo, js}

object Main {

  // require default passive events for scroll/mouse/touch events
  // global.require("default-passive-events")

  def main(args: Array[String]): Unit = {

//    DevOnly {
//      helpers.OutwatchTracing.patch.zipWithIndex.foreach { case ((old, cur), index) =>
//        console.log(s"Snabbdom patch ($index)!", old, cur)
//      }
//    }

    Logging.setup()

    val swUpdateIsAvailable =
     if (!LinkingInfo.developmentMode)
        ServiceWorker.register()
     else Observable.empty

    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
    val state = GlobalState.create(swUpdateIsAvailable)

    // TODO: DevOnly {
    val styleTag = document.createElement("style")
    document.head.appendChild(styleTag)
    styleTag.innerHTML = wust.css.StyleRendering.renderAll
    // }

    OutWatch.renderReplace("#container", MainView(state)).unsafeRunSync()
  }
}
