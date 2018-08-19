package wust.webApp

import monix.reactive.Observable
import org.scalajs.dom.document
import outwatch.dom._
import rx._
import wust.webApp.jsdom.ServiceWorker
import wust.webApp.outwatchHelpers._
import wust.webApp.parsers.NodeDataParser
import wust.webApp.state.GlobalStateFactory
import wust.webApp.views.Elements._
import wust.webApp.views.MainView

import scala.scalajs.LinkingInfo

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
    val state = GlobalStateFactory.create(swUpdateIsAvailable)

    // TODO: DevOnly {
    val styleTag = document.createElement("style")
    document.head.appendChild(styleTag)
    styleTag.innerHTML = wust.css.StyleRendering.renderAll
    // }

    OutWatch.renderReplace("#container", MainView(state)).unsafeRunSync()


    // warming up fastparse parser for faster initial user input
    defer{
      wust.util.time.time("parser warmup") {
        NodeDataParser.addNode("x", Nil, Set.empty)
      }
    }
  }
}
