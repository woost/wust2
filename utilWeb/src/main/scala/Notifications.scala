package wust.utilWeb

import monix.reactive.Observable
import monix.reactive.subjects.ReplaySubject
import org.scalajs.dom.experimental.permissions._
import org.scalajs.dom.experimental.push._
import org.scalajs.dom.experimental.serviceworkers._
import org.scalajs.dom.experimental.{Notification, NotificationOptions}
import org.scalajs.dom.window
import wust.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.typedarray.TypedArrayBufferOps._
import scala.scalajs.js.typedarray._
import scala.util.{Failure, Success}

object Notifications {
  private val permissions = window.navigator.permissions.asInstanceOf[js.UndefOr[Permissions]].toOption
  private val serviceWorker = window.navigator.serviceWorker.asInstanceOf[js.UndefOr[ServiceWorkerContainer]].toOption

  //TODO: this fallback code is difficult to read and we want to model this state as a rx var.
  def permissionStateObservable(implicit ec: ExecutionContext) = {
    permissionStateObservableOf(PushPermissionDescriptor(userVisibleOnly = true)).onErrorHandleWith { // push subscription permission contain notifications
      case t => permissionStateObservableOf(PermissionDescriptor(PermissionName.notifications)) // fallback to normal notification permissions if push permission not available
    }
  }

  def requestPermissions()(implicit ec: ExecutionContext): Unit = {
    subscribeAndPersistWebPush()
    // request notifications permissions as fallback if push permissions did not work in the browser.
    // if it worked, the notification permission are already granted, and will not really request.
    Notification.requestPermission { (state: String) =>
      scribe.info(s"Requested notification permission: $state")
    }
  }

  def notify(title: String, body: Option[String] = None, tag: Option[String] = None)(implicit ec: ExecutionContext): Unit =
    if (Notification.permission.asInstanceOf[PermissionState] != PermissionState.granted) {
      scribe.info(s"Notifications are not granted, cannot send notification: $title")
    } else {
      val options = NotificationOptions(
        body = body.orUndefined,
        tag = tag.orUndefined,
        renotify = tag.isDefined,
        icon = "icon.ico"
      )

      serviceWorker match {
        case Some(serviceWorker) => serviceWorkerNotify(serviceWorker, title, options).foreach { success =>
          if (success) scribe.info(s"Sent notification via ServiceWorker: $title")
          else {
            scribe.info(s"Cannot send notification via ServiceWorker, falling back to browser notify: $title")
            browserNotify(title, options)
          }
        }
        case None =>
          scribe.info(s"ServiceWorker ist not supported by browser, falling back to browser notify: $title")
          browserNotify(title, options)
      }
    }

  private def permissionStateObservableOf(permissionDescriptor: PermissionDescriptor)(implicit ec: ExecutionContext): Observable[PermissionState] = {
    val subject = ReplaySubject[PermissionState]()
    permissions.foreach { (permissions: Permissions) =>
      permissions.query(permissionDescriptor).toFuture.onComplete {
        case Success(desc) =>
          subject.onNext(desc.state)
          desc.asInstanceOf[PermissionStatusWithOnChange].onchange = { _ =>
            subject.onNext(desc.state)
          }
        case Failure(t) =>
          scribe.warn(s"Failed to query permission descriptor for '${permissionDescriptor.name}': $t")
          subject.onError(t)
      }
    }
    subject
  }

  //TODO send message to serviceworker to manage this stuff for us
  private def subscribeAndPersistWebPush()(implicit ec: ExecutionContext): Unit =
    Client.push.getPublicKey().foreach {
      case Some(publicKey) =>
        val publicKeyBytes = new Uint8Array(Base64Codec.decode(publicKey).arrayBuffer())
        val options = PushSubscriptionOptionsWithServerKey(userVisibleOnly = true, applicationServerKey = publicKeyBytes)
        persistPushSubscription(_.subscribe(options))
      case None => scribe.warn("No public key of server available for web push subscriptions. Cannot subscribe to push notifications.")
    }

  private def persistPushSubscription(getSubscription: PushManager => js.Promise[PushSubscription])(implicit ec: ExecutionContext): Unit = serviceWorker match {
    case Some(serviceWorker) => serviceWorker.getRegistration().toFuture.foreach { reg =>
      reg.foreach { reg =>
        getSubscription(reg.pushManager).toFuture.onComplete {
          case Success(sub) if sub != null =>
            //TODO rename p256dh attribute of WebPushSub to publicKey
            val webpush = WebPushSubscription(endpointUrl = sub.endpoint, p256dh = Base64Codec.encode(TypedArrayBuffer.wrap(sub.getKey(PushEncryptionKeyName.p256dh))), auth = Base64Codec.encode(TypedArrayBuffer.wrap(sub.getKey(PushEncryptionKeyName.auth))))
            scribe.info(s"WebPush subscription: $webpush")
            Client.push.subscribeWebPush(webpush)
          case err =>
            scribe.warn(s"Failed to subscribe to push: $err")
        }
      }
    }
    case None =>
      scribe.info("Push notifications are not available in this browser")
      Future.successful(false)
  }

  private def serviceWorkerNotify(serviceWorker: ServiceWorkerContainer, title: String, options: NotificationOptions)(implicit ec: ExecutionContext): Future[Boolean] = {
    serviceWorker.getRegistration().toFuture.flatMap {
      case _registration if _registration != js.undefined =>
        val registration = _registration.asInstanceOf[ServiceWorkerRegistrationWithNotifications]
        registration.showNotification(title, options).toFuture.map { _ => true }.recover { case _ => false }
      case _ => Future.successful(false)
    }.recover { case _ => false }
  }

  private def browserNotify(title: String, options: NotificationOptions): Unit = {
    val n = new Notification(title, options)
  }
}
