package wust.backend.config

import derive.derive
import com.typesafe.config.{Config => TConfig}
import scala.concurrent.duration.Duration

@derive((enableImplicit, tokenLifetime) => toString)
case class AuthConfig(enableImplicit: Boolean, tokenLifetime: Duration, secret: String)

@derive((endpoint, username) => toString)
case class SmtpConfig(endpoint: String, username: String, password: String)
case class EmailConfig(fromAddress: String, smtp: SmtpConfig)

case class Config(auth: AuthConfig, email: Option[EmailConfig], db: TConfig)
object Config {
  import pureconfig._
  import wust.util.Config._

  def load = loadConfig[Config]("wust")
}
