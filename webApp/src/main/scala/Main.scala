package wust.webApp

import org.scalajs.dom.{window, _}
import outwatch.dom._
import rx._
import wust.api.ApiEvent
import wust.graph.GraphChanges
import wust.webApp.outwatchHelpers._

import scala.scalajs.js

object Main {

  // require default passive events for scroll/mouse/touch events
  // global.require("default-passive-events")

  def main(args: Array[String]): Unit = {

    Logging.setup()

    ServiceWorker.register()

    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
    val state = GlobalState.create()

    OutWatch.renderReplace("#container", MainView(state)).unsafeRunSync()
  }
}
