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

  //TODO: modernizr
  def notify(title: String, body: Option[String] = None, tag: Option[String] = None)(implicit ec: ExecutionContext): Unit =
    if (!isGranted) {
      scribe.info(s"Notifications are not granted, cannot send notification: $title")
    } else {
      scribe.info(s"Go notify: $title")
      val options = NotificationOptions(
        body = body.orUndefined,
        tag = tag.orUndefined,
        renotify = tag.isDefined,
        icon = "icon.ico"
      )

      if (window.navigator.serviceWorker != js.undefined) {
        serviceWorkerNotify(title, options).foreach { success =>
          if (success) scribe.info(s"Sent notification via ServiceWorker: $title")
          else {
            scribe.info(s"Cannot send notification via ServiceWorker, falling back to browser notify: $title")
            browserNotify(title, options)
          }
        }
      } else {
        scribe.info(s"ServiceWorker ist not supported by browser, falling back to browser notify: $title")
        browserNotify(title, options)
      }
    }

  private def serviceWorkerNotify(title: String, options: NotificationOptions)(implicit ec: ExecutionContext): Future[Boolean] = {
    window.navigator.serviceWorker.getRegistration().toFuture.flatMap {
      case _registration if _registration != js.undefined =>
        val registration = _registration.asInstanceOf[ServiceWorkerRegistrationWithNotifications]
        registration.showNotification(title, options).toFuture.map { _ => true }.recover { case _ => false }
      case _ => Future.successful(false)
    }.recover { case _ => false }
  }

  private def browserNotify(title: String, options: NotificationOptions): Unit = {
    val n = new Notification(title, options)
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
