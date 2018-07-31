package wust.webApp

import org.scalajs.dom.experimental
import org.scalajs.dom.{experimental, window, _}
import wust.webApp.outwatchHelpers._

import scala.scalajs.js
import scala.util.{Failure, Success}

object SafeDom {
  def Notification: Option[experimental.Notification.type] =
    experimental.Notification.asInstanceOf[js.UndefOr[experimental.Notification.type]].toOption

  object Navigator {
    import org.scalajs.dom.experimental.permissions._
    import org.scalajs.dom.experimental.serviceworkers._

    def permissions: Option[Permissions] = window.navigator.permissions.asInstanceOf[js.UndefOr[Permissions]].toOption
    def serviceWorker: Option[ServiceWorkerContainer] = window.navigator.serviceWorker.asInstanceOf[js.UndefOr[ServiceWorkerContainer]].toOption
    def share: Option[ShareFunction] = window.navigator.asInstanceOf[NavigatorWithShare].share.asInstanceOf[js.UndefOr[ShareFunction]].toOption
  }
}

//TODO contribute to scala-js
@js.native
trait NavigatorWithShare extends js.Any {
  val share: ShareFunction = js.native
}
@js.native
trait ShareFunction extends js.Any {
  def apply(data: ShareData): js.Promise[Unit] = js.native
}
trait ShareData extends js.Object {
  var url: js.UndefOr[String] = js.undefined
  var text: js.UndefOr[String] = js.undefined
  var title: js.UndefOr[String] = js.undefined
}
