package wust.backend.config

import com.typesafe.config.{Config => TConfig}
import scala.concurrent.duration.Duration

//@derive((tokenLifetime) => toString)
case class AuthConfig(tokenLifetime: Duration, secret: String)
{
  // Don't write secret in logs
  override def toString: String = s"AuthConfig($tokenLifetime)"
}

//@derive((endpoint, username) => toString)
case class SmtpConfig(endpoint: String, username: String, password: String)
{
  // Don't write password in logs
  override def toString: String = s"SmtpConfig($endpoint, $username)"
}
case class EmailConfig(fromAddress: String, smtp: SmtpConfig)

case class ServerConfig(port: Int, clientBufferSize: Int)

case class Config(server: ServerConfig, auth: AuthConfig, email: Option[EmailConfig], db: TConfig) {
  override def toString = {
    val cleanDb = db.withoutPath("password")
    s"Config($auth, $email, $cleanDb)"
  }
}

object Config {
  import pureconfig._
  import wust.util.Config._

  def load = loadConfig[Config]("wust.core")
}
