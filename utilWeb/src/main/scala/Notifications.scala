package wust.utilWeb

import org.scalajs.dom.window
import org.scalajs.dom.console
import org.scalajs.dom.experimental.{ Notification, NotificationOptions }
import org.scalajs.dom.experimental.push._
import org.scalajs.dom.experimental.serviceworkers._
import scalajs.js
import org.scalajs.dom
import wust.api._
import scalajs.js.JSConverters._
import scala.concurrent.{ Promise, Future, ExecutionContext }
import scala.util.{ Success, Failure }
import scala.scalajs.js.typedarray._, TypedArrayBufferOps._
import window.{atob,btoa}
import java.nio.ByteBuffer

object Notifications extends Notifications

object Base64Codec {
  import js.Dynamic.{global => g}

  def encode(buffer: ByteBuffer) = {
    val s = new StringBuilder(buffer.limit)
    for (i <- 0 until buffer.limit) {
      val c = buffer.get
      s ++= g.String.fromCharCode(c & 0xFF).asInstanceOf[String]
    }

    btoa(s.result)
  }

  def decode(data: String): ByteBuffer = {
    val byteString = atob(data)
    val buffer = ByteBuffer.allocateDirect(byteString.size)
    byteString.foreach(c => buffer.put(c.toByte))
    buffer.flip()
    buffer
  }
}


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


  //TODO
  private val serverKey = new Uint8Array(Base64Codec.decode("BDP21xA+AA6MyDK30zySyHYf78CimGpsv6svUm0dJaRgAjonSDeTlmE111Vj84jRdTKcLojrr5NtMlthXkpY+q0").arrayBuffer())
  def subscribeWebPush()(implicit ec: ExecutionContext): Unit =
    if (window.navigator.serviceWorker == js.undefined) {
      scribe.info("Push notifications are not available in this browser")
    } else {
      console.log("AS", serverKey)
      console.log("AS", PushSubscriptionOptionsWithServerKey(userVisibleOnly = true, applicationServerKey = serverKey))

      scribe.info("GO")
      window.navigator.serviceWorker.getRegistration().toFuture.foreach { reg =>
        scribe.info("REG YOU" + reg)
        reg.foreach { reg =>
          scribe.info("REG YOU, too" + reg)
          reg.pushManager.subscribe(PushSubscriptionOptionsWithServerKey(userVisibleOnly = true, applicationServerKey = serverKey)).toFuture.onComplete {
            case Success(sub) =>
              scribe.info(s"Got subscription: $sub")
              if (sub != js.undefined) {
                //TODO rename p256dh attribute of WebPushSub to publicKey
                val webpush = WebPushSubscription(endpointUrl = sub.endpoint, p256dh = Base64Codec.encode(TypedArrayBuffer.wrap(sub.getKey(PushEncryptionKeyName.p256dh))), auth = Base64Codec.encode(TypedArrayBuffer.wrap(sub.getKey(PushEncryptionKeyName.auth))))
                scribe.info(s"WebPush subscription: $webpush")
                Client.api.subscribeWebPush(webpush)
              }
            case Failure(t) => scribe.info(s"Failed to subscribe to push: $t")
          }
        }
      }
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
