package wust.webApp

import outwatch.dom._
import rx._
import wust.webApp.outwatchHelpers._

import scala.scalajs.{LinkingInfo, js}

object Main {

  // require default passive events for scroll/mouse/touch events
  // global.require("default-passive-events")

  def main(args: Array[String]): Unit = {

    Logging.setup()

    val swUpdateIsAvailable =
      if (!LinkingInfo.developmentMode) ServiceWorker.register()
      else Observable.empty

    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
    val state = GlobalState.create(swUpdateIsAvailable)

    OutWatch.renderReplace("#container", MainView(state)).unsafeRunSync()
  }
}
