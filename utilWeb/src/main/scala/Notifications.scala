package wust.utilWeb

import org.scalajs.dom.window
import org.scalajs.dom.experimental.{Notification, NotificationOptions}
import org.scalajs.dom.experimental.serviceworkers._
import scalajs.js
import org.scalajs.dom
import scalajs.js.JSConverters._
import scala.concurrent.{Promise, Future, ExecutionContext}
import scala.util.{Success, Failure}

object Notifications extends Notifications

class Notifications {
  // https://developer.mozilla.org/en-US/docs/Web/API/Notifications_API/Using_the_Notifications_API
  def isGranted = Notification.permission == "granted"
  def isDenied = Notification.permission == "denied"

  def requestPermissions(): Future[String] = {
    val promise = Promise[String]()
    Notification.requestPermission { (permission: String) =>
      scribe.info(s"Requested permission: $permission")
      promise success permission
    }
    promise.future
  }

  def serviceWorkerNotify(title: String, body: Option[String] = None)(implicit ec: ExecutionContext): Unit = if (isGranted) {
    window.navigator.serviceWorker.getRegistration().toFuture.onComplete {
      case Success(_registration) if _registration != js.undefined =>
        val registration = _registration.asInstanceOf[ServiceWorkerRegistrationWithNotifications]
        registration.showNotification(title, NotificationOptions(body = body.orUndefined)).toFuture.foreach { event =>
          scribe.info(s"Send out notification via service worker: $event")
        }
      case _ =>
        scribe.warn("Cannot send notification via service worker, falling back to browser notification")
        browserNotify(title, body)
    }
  }

  def browserNotify(title: String, body: Option[String] = None, onclick: Notification => Any = _ => ()): Unit = if (isGranted) {
    val n = new Notification(title, NotificationOptions(body = body.orUndefined))
    scribe.info(s"Send out notification via browser: $n")
    // def fire() {
    //   Analytics.sendEvent("notification", "fired", "pwa")
    //   val n = new Notification(title, NotificationOptions(body = body.orUndefined))
    //   n.addEventListener[Event]("click", { (event: Event) =>
    //   onclick(event.target.asInstanceOf[Notification])
    //     Analytics.sendEvent("notification", "clicked", "pwa")
    //   })
    // }
    // if (notificationsDenied) {
    // } else if (notificationsGranted) {
    //   fire()
    // } else {
    //   Notification.requestPermission { (permission: String) =>
    //     Analytics.sendEvent("notification", "permissionrequested", "pwa")
    //     if (permission == "granted") {
    //       Analytics.sendEvent("notification", "permissiongranted", "pwa")
    //       fire()
    //     }
    //   }
    // }
  }
}
