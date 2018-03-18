package wust.utilWeb

import org.scalajs.dom.window
import org.scalajs.dom.console
import org.scalajs.dom.experimental.{Notification, NotificationOptions}
import org.scalajs.dom.experimental.push._
import org.scalajs.dom.experimental.serviceworkers._
import org.scalajs.dom.experimental.permissions._

import scalajs.js
import org.scalajs.dom
import wust.api._
import wust.utilWeb.outwatchHelpers.RichObservable

import scalajs.js.JSConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}
import scala.scalajs.js.typedarray._
import TypedArrayBufferOps._
import window.{atob, btoa}
import java.nio.ByteBuffer

import cats.data.OptionT
import cats.implicits._
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom.raw.{IDBDatabase, IDBFactory, IDBObjectStore, IDBRequest}
import rx.{Rx, Var}

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

object IndexedDbOps {
  private val indexedDb = window.indexedDB.asInstanceOf[js.UndefOr[IDBFactory]].toOption

  object stores {
    val auth = "auth"
  }

  private lazy val db = OptionT(indexedDb.fold(Future.successful(Option.empty[IDBDatabase])) { indexedDb =>
    val openreq = indexedDb.open("woost", 1)
    openreq.onupgradeneeded = { e =>
      val db = openreq.result.asInstanceOf[IDBDatabase]
      db.createObjectStore(stores.auth)
    }
    requestFuture[IDBDatabase](openreq)
  })

  def storeAuth(auth: Authentication)(implicit ec: ExecutionContext): Future[Boolean] = auth match {
    case Authentication.Verified(_, _, token) => onStore(stores.auth) { store =>
      store.put(token, 0)
    }
    case _ => onStore(stores.auth) { store =>
      store.delete(0)
    }
  }

  private def onStore(storeName: String)(f: IDBObjectStore => IDBRequest)(implicit ec: ExecutionContext): Future[Boolean] =
    db.flatMapF { db =>
      val transaction = db.transaction(Array(storeName).toJSArray, "readwrite")
      val store = transaction.objectStore(storeName)
      requestFuture(f(store))
    }.value.map((opt: Option[Any]) => opt.isDefined).recover { case _ => false }

  private def requestFuture[T](request: IDBRequest): Future[Option[T]] = {
    val promise = Promise[Option[T]]()
    request.onsuccess = { _ =>
      promise success Option(request.result.asInstanceOf[T])
    }
    request.onerror = { _ =>
      scribe.warn(s"IndexedDb request failed: ${request.error}")
      promise success None
    }
    promise.future
  }
}

object Notifications {
  private val permissions = window.navigator.permissions.asInstanceOf[js.UndefOr[Permissions]].toOption
  private val serviceWorker = window.navigator.serviceWorker.asInstanceOf[js.UndefOr[ServiceWorkerContainer]].toOption

  private def permissionStateObservableOf(permissionDescriptor: PermissionDescriptor)(implicit ec: ExecutionContext): Observable[PermissionState] = {
    val subject = PublishSubject[PermissionState]()
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

  def permissionStateObservable(implicit ec: ExecutionContext) = {
    permissionStateObservableOf(PushPermissionDescriptor(userVisibleOnly = true)).onErrorHandleWith { // push subscription permission contain notifications
      case t => permissionStateObservableOf(PermissionDescriptor(PermissionName.notifications)) // fallback to normal notification permissions if push permission not available
    }
  }

  def requestPermissions()(implicit ec: ExecutionContext): Unit = {
    subscribeAndPersistWebPush()
    Notification.requestPermission { (state: String) =>
      scribe.info(s"Requested notification permission: $state")
    }
  }

  //TODO
  private val serverKey = new Uint8Array(Base64Codec.decode("BDP21xA+AA6MyDK30zySyHYf78CimGpsv6svUm0dJaRgAjonSDeTlmE111Vj84jRdTKcLojrr5NtMlthXkpY+q0").arrayBuffer())

  private def subscribeAndPersistWebPush()(implicit ec: ExecutionContext): Unit =
    persistPushSubscription(_.subscribe(PushSubscriptionOptionsWithServerKey(userVisibleOnly = true, applicationServerKey = serverKey)))

  private def persistPushSubscription(getSubscription: PushManager => js.Promise[PushSubscription])(implicit ec: ExecutionContext): Unit = serviceWorker match {
    case Some(serviceWorker) => serviceWorker.getRegistration().toFuture.foreach { reg =>
      reg.foreach { reg =>
        getSubscription(reg.pushManager).toFuture.onComplete {
          case Success(sub) if sub != null =>
            //TODO rename p256dh attribute of WebPushSub to publicKey
            val webpush = WebPushSubscription(endpointUrl = sub.endpoint, p256dh = Base64Codec.encode(TypedArrayBuffer.wrap(sub.getKey(PushEncryptionKeyName.p256dh))), auth = Base64Codec.encode(TypedArrayBuffer.wrap(sub.getKey(PushEncryptionKeyName.auth))))
            scribe.info(s"WebPush subscription: $webpush")
            Client.api.subscribeWebPush(webpush)
          case err =>
            scribe.warn(s"Failed to subscribe to push: $err")
        }
      }
    }
    case None =>
      scribe.info("Push notifications are not available in this browser")
      Future.successful(false)
  }

  def notify(title: String, body: Option[String] = None, tag: Option[String] = None)(implicit ec: ExecutionContext): Unit =
    if (Notification.permission.asInstanceOf[PermissionState] != PermissionState.granted) {
      scribe.info(s"Notifications are not granted, cannot send notification: $title")
    } else {
      scribe.info(s"Go notify: $title")
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
