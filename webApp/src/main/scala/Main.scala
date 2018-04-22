package wust.webApp

import org.scalajs.dom._

import scala.scalajs.js.Dynamic.global
import scala.scalajs.js
import wust.api.{ApiEvent, Authentication}
import wust.ids._
import wust.graph.{Graph, GraphChanges, Page}
import wust.webApp._
import outwatch.dom._
import dsl._
import wust.webApp.outwatchHelpers._
import wust.webApp.views._
import rx._
import cats._
import wust.webApp.views.graphview.GraphView
import scala.util.{Success,Failure}

object Main {

  // require default passive events for scroll/mouse/touch events
  // global.require("default-passive-events")

  def main(args: Array[String]): Unit = {
    Logging.setup()

    ServiceWorker.register()


    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    val state = new GlobalState()

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

object ServiceWorker {
  import org.scalajs.dom.experimental.serviceworkers._

  def register():Unit = {
    // Check that service workers are registered
    // TODO: check if serviceWorker exists in navigator. probably with js.Dynamic
    // if ('serviceWorker' in navigator) {
      // Use the window load event to keep the page load performant
      window.addEventListener("load", (_:Any) => {
        //TODO: disable HTTP-cache for this service-worker
        window.navigator.asInstanceOf[ServiceWorkerNavigator].serviceWorker.register("sw.js").toFuture.onComplete { 
          case Success(registration) => console.log("SW registered: ", registration)
          case Failure(registrationError) => console.warn("SW registration failed: ", registrationError.toString)
        }
      })
    // }
  }
}
