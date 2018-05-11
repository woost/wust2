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

    state.currentAuth.foreach(IndexedDbOps.storeAuth)

    Client.observable.event.foreach { events =>
      val changes = events.collect { case ApiEvent.NewGraphChanges(changes) => changes }.foldLeft(GraphChanges.empty)(_ merge _)
      if (changes.addPosts.nonEmpty) {
        val msg = if (changes.addPosts.size == 1) "New Post" else s"New Post (${changes.addPosts.size})"
        val body = changes.addPosts.map(_.content).mkString(", ")
        Notifications.notify(msg, body = Some(body), tag = Some("new-post"))
      }
    }

    OutWatch.renderReplace("#container", MainView(state)).unsafeRunSync()
  }
}
