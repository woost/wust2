package wust.utilWeb

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import org.scalajs.dom.experimental.{ Notification, NotificationOptions }
import org.scalajs.dom.experimental.serviceworkers.ServiceWorkerRegistration
import org.scalajs.dom

//TODO: pr scala-js-dom: https://github.com/scala-js/scala-js-dom/pull/325
@js.native
trait ServiceWorkerRegistrationWithNotifications extends ServiceWorkerRegistration {
  def getNotifications(options: GetNotificationOptions = ???): js.Promise[js.Array[Notification]] = js.native
  def showNotification(title: String, options: NotificationOptions = ???): js.Promise[dom.Event] = js.native
}

@js.native
@JSGlobal
class GetNotificationOptions(val tag: String) extends js.Object

object GetNotificationOptions {
  def apply(tag: String): GetNotificationOptions = {
    js.Dynamic
      .literal("tag" -> tag)
      .asInstanceOf[GetNotificationOptions]
  }
}
