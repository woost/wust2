package wust.backend.config

import derive.derive
import com.typesafe.config.{Config => TConfig}
import scala.concurrent.duration.Duration

@derive((tokenLifetime) => toString)
case class AuthConfig(tokenLifetime: Duration, secret: String)

@derive((endpoint, username) => toString)
case class SmtpConfig(endpoint: String, username: String, password: String)
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

  def load = loadConfig[Config]("wust")
}
