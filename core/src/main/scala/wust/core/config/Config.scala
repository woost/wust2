package wust.core.config

import com.typesafe.config.{Config => TConfig}
import wust.serviceUtil.Logging

import scala.concurrent.duration.Duration

final case class AuthConfig(tokenLifetime: Duration, secret: String) {
  override def toString: String = s"AuthConfig($tokenLifetime, ***)"
}

//TODO: tagged types for public/private key
final case class VapidKeys(publicKey: String, privateKey: String) {
  override def toString: String = s"VapidKeys($publicKey, ***)"
}

final case class AppKeys(appKey: String, appSecret: String) {
  override def toString: String = s"AppKeys($appKey, ***)"
}

final case class StripeConfig(endpointSecret: String, apiKey: String, publicKey: String) {
  override def toString: String = s"StripeConfig(***, ***, $publicKey)"
}

final case class PushedConfig(keys: AppKeys)

final case class WebPushConfig(subject: String, keys: VapidKeys)

final case class PushNotificationConfig(webPush: Option[WebPushConfig], pushed: Option[PushedConfig])

final case class SmtpConfig(endpoint: String, username: String, password: String) {
  override def toString: String = s"SmtpConfig($endpoint, $username, ***)"
}
final case class EmailSettings(fromAddress: String, blockedEmailDomains: Option[String]) {
  def blockedEmailDomainsList = blockedEmailDomains.fold(Array.empty[String])(_.split(" "))
}
final case class EmailConfig(settings: EmailSettings, smtp: SmtpConfig)

final case class SESConfig(settings: EmailSettings, region: String)

final case class AwsConfig(uploadBucketName: String, region: String, ses: Option[SESConfig])

final case class ServerConfig(host: String, port: Int, clientBufferSize: Int)

final case class Config(
    server: ServerConfig,
    aws: Option[AwsConfig],
    pushNotification: Option[PushNotificationConfig],
    auth: AuthConfig,
    email: Option[EmailConfig],
    logstash: Option[Logging.LogstashConfig],
    stripe: Option[StripeConfig],
    db: TConfig
) {
  override def toString: String = {
    val cleanDb = db.withoutPath("password")
    s"Config($server, $aws, $pushNotification, $auth, $email, $logstash, $cleanDb)"
  }
}

object Config {
  import pureconfig._
  import wust.util.Config._
  import pureconfig.generic.auto._

  def load = loadConfig[Config]("wust.core")
}
