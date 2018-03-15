package wust.utilWeb

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom.experimental.{ Notification, NotificationOptions }
import org.scalajs.dom.experimental.serviceworkers.ServiceWorkerRegistration
import org.scalajs.dom

//TODO: pr scala-js-dom
@js.native
trait ServiceWorkerRegistrationWithNotifications extends ServiceWorkerRegistration {
  def getNotifications(options: GetNotificationOptions = ???): js.Promise[js.Array[Notification]] = js.native
  //TODO: NotificationEvent
  def showNotification(title: String, options: NotificationOptions = ???): js.Promise[dom.Event] = js.native
}

@js.native
class GetNotificationOptions(val tag: String) extends js.Object

object GetNotificationOptions {
  def apply(tag: String): GetNotificationOptions = {
    js.Dynamic
      .literal("tag" -> tag)
      .asInstanceOf[GetNotificationOptions]
  }
}
