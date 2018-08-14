package wust.webApp

import monix.reactive.Observable
import outwatch.dom._
import rx._
import wust.webApp.outwatchHelpers._
import org.scalajs.dom.{console, document, window}
import wust.webApp.parsers.NodeDataParser
import wust.webApp.views.Elements._

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

    if(js.Dynamic.global.ga.isInstanceOf[js.Function])
      console.log("Loaded Google Analytics:", js.Dynamic.global.ga.asInstanceOf[js.Any])

    if(js.Dynamic.global.gtag.isInstanceOf[js.Function])
      console.log("Loaded Google Tag Manager:", js.Dynamic.global.gtag.asInstanceOf[js.Any])
  }
}
