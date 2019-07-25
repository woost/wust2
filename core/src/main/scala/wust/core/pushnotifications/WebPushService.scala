package wust.core.pushnotifications

import java.security.Security

import nl.martijndwars.webpush
import nl.martijndwars.webpush.ClosableCallback
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

final case class PushData(username: String, content: String, nodeId: String, subscribedId: String, subscribedContent: String, parentId: Option[String], parentContent: Option[String], epoch: String) {
  import wust.util.StringOps._
  def trimToSize: PushData = this.copy(
    username = trimToMaxLength(username, VisiblePushData.maxTitleLength),
    content = trimToMaxLength(content, VisiblePushData.maxContentLength),
    subscribedContent = trimToMaxLength(subscribedContent, VisiblePushData.maxTitleLength),
    parentContent = parentContent.map(trimToMaxLength(_, VisiblePushData.maxTitleLength)),
  )
}

class WebPushService private(config: WebPushConfig) {

  private def base64UrlSafe(s: String) = s.replace("/", "_").replace("+", "-")

  //TODO: expiry?
  private val service = new webpush.PushService(base64UrlSafe(config.keys.publicKey), base64UrlSafe(config.keys.privateKey), config.subject)

  // we write our own version, because service.send is synchronous and service.sendAsync returns a stupid java-future.
  // so we do the same as sendAsync, but inject our own callback that completes a promise.
  private def doSend(notification: webpush.Notification)(implicit ec: ExecutionContext): Future[HttpResponse] = Future {
    val httpPost = service.preparePost(notification)
    val closeableHttpAsyncClient = HttpAsyncClients.createSystem()
    closeableHttpAsyncClient.start()

    // callback for closing the http client
    val closeCb = new ClosableCallback(closeableHttpAsyncClient)
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

  def send(sub: Data.WebPushSubscription, payload: String)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    val notification = new webpush.Notification(
      sub.endpointUrl,
      base64UrlSafe(sub.p256dh),
      base64UrlSafe(sub.auth),
      payload.take(VisiblePushData.maxTotalLength)
    )

    scribe.info(s"Sending push notification to ${sub}: $payload")
    doSend(notification)
  }

  def send(sub: Data.WebPushSubscription, payload: PushData)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    import io.circe.generic.auto._
    import io.circe.syntax._

    val notification = new webpush.Notification(
      sub.endpointUrl,
      base64UrlSafe(sub.p256dh),
      base64UrlSafe(sub.auth),
      payload.trimToSize.asJson.noSpaces
    )

    scribe.info(s"Sending push notification to ${sub}: ${payload.content}")
    doSend(notification)
  }
}
object WebPushService {
  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  def apply(c: WebPushConfig): WebPushService = new WebPushService(c)
}

