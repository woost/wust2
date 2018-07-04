package wust.webApp

import org.scalajs.dom.{experimental, window, _}
import wust.webApp.outwatchHelpers._

import scala.scalajs.js
import scala.util.{Failure, Success}

object SafeDom {
  val Notification =
    experimental.Notification.asInstanceOf[js.UndefOr[experimental.Notification.type]]
}

object Navigator {
  import org.scalajs.dom.experimental.permissions._
  import org.scalajs.dom.experimental.serviceworkers._

  val permissions = window.navigator.permissions.asInstanceOf[js.UndefOr[Permissions]].toOption
  val serviceWorker =
    window.navigator.serviceWorker.asInstanceOf[js.UndefOr[ServiceWorkerContainer]].toOption
}
