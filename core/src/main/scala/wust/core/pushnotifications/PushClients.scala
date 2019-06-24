package wust.core.pushnotifications

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import wust.core.config.PushNotificationConfig

case class PushClients(
  webPushService: Option[WebPushService],
  pushedClient: Option[PushedClient]
)
object PushClients {
  def apply(config: PushNotificationConfig)(implicit system: ActorSystem, materializer: ActorMaterializer): PushClients = PushClients(
    config.webPush.map(WebPushService.apply),
    config.pushed.map(PushedClient.apply)
  )
}
