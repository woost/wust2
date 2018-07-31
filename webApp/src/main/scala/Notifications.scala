package wust.webApp

import org.scalajs.dom.experimental
import org.scalajs.dom.experimental.NotificationOptions
import org.scalajs.dom.experimental.permissions._
import org.scalajs.dom.experimental.push._
import rx._
import wust.api._
import wust.webApp.SafeDom._

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.typedarray.TypedArrayBufferOps._
import scala.scalajs.js.typedarray._
import scala.util.{Failure, Success}

object Notifications {
  import Navigator._

  def cancelSubscription()(implicit ec: ExecutionContext): Unit = {
    WebPush.cancelAndPerist()
  }
  def refreshSubscription()(implicit ec: ExecutionContext): Unit = {
    WebPush.getSubscriptionAndPersist()
  }
  def requestPermissionsAndSubscribe()(implicit ec: ExecutionContext): Unit = {
    WebPush.subscribeAndPersist()

    // request notifications permissions as fallback if push permissions did not work in the browser.
    // if it worked, the notification permission are already granted, and will not really request.
    Notification.foreach(_.requestPermission { (state: String) =>
      scribe.info(s"Requested notification permission: $state")
    })
  }

  def notify(title: String, body: Option[String] = None, tag: Option[String] = None)(
      implicit ec: ExecutionContext
  ): Unit = Notification match {
    case None =>
      scribe.info(s"Notifications are not supported in browser, cannot send notification: $title")
    case Some(n) if n.permission.asInstanceOf[PermissionState] != PermissionState.granted =>
      scribe.info(s"Notifications are not granted, cannot send notification: $title")
    case Some(n) =>
      val options = NotificationOptions(
        body = body.orUndefined,
        tag = tag.orUndefined,
        renotify = tag.isDefined,
        icon = "favicon.ico"
      )

      browserNotify(title, options)
  }

  //TODO: this fallback code is difficult to read and we want to model this state as a rx var.
  def createPermissionStateRx()(implicit ec: ExecutionContext, ctx: Ctx.Owner) = {
    // TODO: we need to disbable permissiondescriptor for push-notifications, as firefox only supports normal notification as a permissiondescriptor when using permissions.query to get a change event.
    // As push notification contains permissions for normal notifications, this should be enough.
    //permissionStateRxOf(PushPermissionDescriptor(userVisibleOnly = true)).onErrorHandleWith { // push subscription permission contain notifications
    //case t =>
    permissionStateRxOf(PermissionDescriptor(PermissionName.notifications)) // fallback to normal notification permissions if push permission not available
    //}
  }

  private def permissionStateRxOf(
      permissionDescriptor: PermissionDescriptor
  )(implicit ec: ExecutionContext, ctx: Ctx.Owner): Rx[PermissionState] = {
    Notification match {
      case Some(n) =>
        val subject = Var[PermissionState](n.permission.asInstanceOf[PermissionState])
        permissions.foreach { (permissions: Permissions) =>
          permissions.query(permissionDescriptor).toFuture.onComplete {
            case Success(desc) =>
              subject() = desc.state
              desc.onchange = { _ =>
                subject() = desc.state
              }
            case Failure(t) =>
              scribe.warn(s"Failed to query permission descriptor for '${permissionDescriptor.name}': $t")
          }
        }
        subject
      case None => Rx[PermissionState](PermissionState.denied)
    }
  }

  private def browserNotify(title: String, options: NotificationOptions): Unit = {
    new experimental.Notification(title, options)
    ()
  }

  //TODO send message to serviceworker to manage this stuff for us
  private object WebPush {
    //TODO retry if failed with Task
    private lazy val serverPublicKey = Client.push.getPublicKey()

    def cancelAndPerist()(implicit ec: ExecutionContext): Unit =
      persistPushSubscription(_.getSubscription(), Client.push.cancelSubscription)

    def getSubscriptionAndPersist()(implicit ec: ExecutionContext): Unit =
      persistPushSubscription(_.getSubscription(), Client.push.subscribeWebPush)

    def subscribeAndPersist()(implicit ec: ExecutionContext): Unit = serverPublicKey.foreach {
      case Some(publicKey) =>
        val publicKeyBytes = new Uint8Array(Base64Codec.decode(publicKey).arrayBuffer())
        val options = new PushSubscriptionOptions {
          userVisibleOnly = true
          applicationServerKey = publicKeyBytes
        }
        persistPushSubscription(_.subscribe(options), Client.push.subscribeWebPush)
      case None =>
        scribe.warn("No public key of server available for web push subscriptions. Cannot subscribe to push notifications.")
    }

    private def persistPushSubscription(
                                         getSubscription: PushManager => js.Promise[PushSubscription],
                                         sendSubscription: WebPushSubscription => Future[Boolean]
                                       )(implicit ec: ExecutionContext): Unit = serviceWorker match {
      case Some(serviceWorker) => serviceWorker.getRegistration().toFuture.onComplete {
        case Success(reg) => reg.toOption match {
          case Some(reg) =>
            getSubscription(reg.pushManager).toFuture.onComplete {
              case Success(sub) if sub != null =>
                //TODO rename p256dh attribute of WebPushSub to publicKey
                val webpush = WebPushSubscription(
                  endpointUrl = sub.endpoint,
                  p256dh = Base64Codec.encode(
                    TypedArrayBuffer.wrap(sub.getKey(PushEncryptionKeyName.p256dh))),
                  auth = Base64Codec.encode(
                    TypedArrayBuffer.wrap(sub.getKey(PushEncryptionKeyName.auth))))
                scribe.info(s"WebPush subscription: $webpush")
                sendSubscription(webpush)
              case err =>
                scribe.warn(s"Failed to subscribe to push: $err")
            }
          case None =>
            scribe.warn(s"Got empty service worker registration")
        }
        case err =>
          scribe.warn(s"Failed to get service worker registration: $err")
      }
      case None =>
        scribe.info("Push notifications are not available in this browser")
    }
  }
}
