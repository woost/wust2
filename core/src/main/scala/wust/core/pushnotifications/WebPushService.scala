package wust.core.pushnotifications

import java.security.Security

import nl.martijndwars.webpush
import org.apache.http.HttpResponse
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.HttpAsyncClients
import wust.core.config.WebPushConfig
import wust.db.Data

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

/**
  * Max Payload
  *   - Android: 2-4kB
  *   - iOS: 2-4kB
  *   - Microsoft: 2-5kB
  *   -> 2kB should be save
  * Length of variables = 76
  * Number of variables = 8
  * json overhead pre variable = 2 -> {}, 2 -> "", 1 -> :, 2 -> "" = 7
  * const json overhead = 2
  * overhead ~ 76 + 56 + 2
  * metaLength ~ username.length + nodeId.length + subscribedId.length + parentId.getOrElse("null").length + epoch.length
  */

object VisiblePushData {
  val maxTitleLength = 60
  val maxContentLength = 600
  val maxTotalLength = 2048
}

//TODO: refactor this class, needs to be in sync with sw.js (also migrate for older sw.js).
//TODO: parent is not an option anymore
// the version specifies compatibility with the serviceworker. bump the version in serviceworker/index.js if bumped here. This should be changed if the structure of this document changes in a way that old sw cannot read it anymore.
final case class PushData(username: String, content: String, nodeId: String, subscribedId: String, subscribedContent: String, epoch: Long, description: String, version: Int = 1) {
  import wust.util.StringOps._
  def trimToSize: PushData = this.copy(
    username = trimToMaxLength(username, VisiblePushData.maxTitleLength),
    content = trimToMaxLength(content, VisiblePushData.maxContentLength),
    subscribedContent = trimToMaxLength(subscribedContent, VisiblePushData.maxTitleLength),
  )
}

class WebPushService private(config: WebPushConfig) {

  private def base64UrlSafe(s: String): String = s.replace("/", "_").replace("+", "-")

  //TODO: expiry?
  private val service = new webpush.PushService(base64UrlSafe(config.keys.publicKey), base64UrlSafe(config.keys.privateKey), config.subject)

  // we write our own version, because service.send is synchronous and service.sendAsync returns a stupid java-future.
  // so we do the same as sendAsync, but inject our own callback that completes a promise.
  private def doSend(notification: webpush.Notification)(implicit ec: ExecutionContext): Future[HttpResponse] = Future {
    val httpPost = service.preparePost(notification, webpush.Encoding.AESGCM) // AESGCM is default of service.send in web-push library
    val closeableHttpAsyncClient = HttpAsyncClients.createSystem()
    closeableHttpAsyncClient.start()

    // callback for closing the http client
    val closeCb = new webpush.ClosableCallback(closeableHttpAsyncClient)
    val promise = Promise[HttpResponse]()
    closeableHttpAsyncClient.execute(httpPost, new FutureCallback[HttpResponse] {
      override def completed(result: HttpResponse): Unit = {
        promise success result
        closeCb.completed(result)
      }
      override def failed(ex: Exception): Unit = {
        promise failure ex
        closeCb.failed(ex)
      }
      override def cancelled(): Unit = {
        promise failure new Exception("HttpAsync request got cancelled.")
        closeCb.cancelled()
      }
    })

    promise.future
  }.flatten //2.13: Future.delegate

  private def createNotification(sub: Data.WebPushSubscription, payload: String): webpush.Notification = {
    new webpush.Notification(
      sub.endpointUrl,
      webpush.Utils.loadPublicKey(base64UrlSafe(sub.p256dh)),
      webpush.Base64Encoder.decode(base64UrlSafe(sub.auth)),
      payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
      43200 // ttl seconds, 12h
    )
  }

  def send(sub: Data.WebPushSubscription, payload: String)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    val notification = createNotification(sub, payload.take(VisiblePushData.maxTotalLength))
    scribe.info(s"Sending push notification to ${sub}: $payload")
    doSend(notification)
  }

  def send(sub: Data.WebPushSubscription, payload: PushData)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    import io.circe.generic.auto._
    import io.circe.syntax._

    val notification = createNotification(sub, payload.trimToSize.asJson.noSpaces)
    scribe.info(s"Sending push notification to ${sub}: ${payload.content}")
    doSend(notification)
  }
}
object WebPushService {
  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  def apply(c: WebPushConfig): WebPushService = new WebPushService(c)
}

