package wust.backend

import java.security.Security

import nl.martijndwars.webpush
import nl.martijndwars.webpush.ClosableCallback
import org.apache.http.HttpResponse
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.HttpAsyncClients
import wust.backend.config.PushNotificationConfig
import wust.db.Data

import scala.concurrent.{Future, Promise}
import scala.util.Try

case class PushData(username: String, content: String, nodeId: String, parentContent: Option[String], parentId: Option[String])

class PushService private(service: webpush.PushService) {
  // we write our own version, because service.send is synchronous and service.sendAsync returns a stupid java-future.
  // so we do the same as sendAsync, but inject our own callback that completes a promise.
  private def doSend(notification: webpush.Notification): Try[Future[HttpResponse]] = Try {
    val httpPost = service.preparePost(notification)
    val closeableHttpAsyncClient = HttpAsyncClients.createSystem();
    closeableHttpAsyncClient.start();

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
  }

  def send(sub: Data.WebPushSubscription, payload: String): Future[HttpResponse] = {
    val notification = new webpush.Notification(
      sub.endpointUrl,
      PushService.base64UrlSafe(sub.p256dh),
      PushService.base64UrlSafe(sub.auth),
      payload
    )

    doSend(notification).toEither.fold(Future.failed, identity)
  }

  def send(sub: Data.WebPushSubscription, payload: PushData): Future[HttpResponse] = {
    import io.circe.parser._
    import io.circe.syntax._
    import io.circe.generic.auto._

    val notification = new webpush.Notification(
      sub.endpointUrl,
      PushService.base64UrlSafe(sub.p256dh),
      PushService.base64UrlSafe(sub.auth),
      payload.asJson.noSpaces
    )


    doSend(notification).toEither.fold(Future.failed, identity)
  }
}
object PushService {
  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  private def base64UrlSafe(s: String) = s.replace("/", "_").replace("+", "-")

  //TODO: expiry?
  def apply(c: PushNotificationConfig): PushService = new PushService(
    new webpush.PushService(base64UrlSafe(c.keys.publicKey), base64UrlSafe(c.keys.privateKey), c.subject)
  )
}
