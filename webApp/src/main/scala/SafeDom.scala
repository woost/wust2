package wust.webApp

import org.scalajs.dom.{window, _}
import outwatch.dom._
import rx._
import wust.api.ApiEvent
import wust.graph.GraphChanges
import org.scalajs.dom.experimental
import wust.webApp.outwatchHelpers._

import scala.scalajs.js
import scala.util.{Failure, Success}

object SafeDom {
  val Notification = experimental.Notification.asInstanceOf[js.UndefOr[experimental.Notification.type]]
}

object Navigator {
  import org.scalajs.dom.experimental.permissions._
  import org.scalajs.dom.experimental.serviceworkers._

  val permissions = window.navigator.permissions.asInstanceOf[js.UndefOr[Permissions]].toOption
  val serviceWorker = window.navigator.serviceWorker.asInstanceOf[js.UndefOr[ServiceWorkerContainer]].toOption
}

object ServiceWorker {

  def register():Unit = {
    // Use the window load event to keep the page load performant
    Navigator.serviceWorker.foreach { sw =>
      window.addEventListener("load", (_:Any) => {
        sw.register("sw.js").toFuture.onComplete { 
          case Success(registration) => console.log("SW registered: ", registration)
          case Failure(registrationError) => console.warn("SW registration failed: ", registrationError.toString)
        }
      })
    }
  }
}
