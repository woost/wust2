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
  import com.typesafe.config.ConfigValue
  import pureconfig.syntax.PimpedConfigValue
  import pureconfig.error.KeyNotFound

  implicit def hint[T] =
    ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

  // if only keyNotFound errors for an optional value, then None
  implicit def optionConfigReader[T](implicit reader: Derivation[ConfigReader[T]]): ConfigReader[Option[T]] =
    new ConfigReader[Option[T]] {
      override def from(config: ConfigValue) = config.to[T] match {
        case Right(config) => Right(Some(config))
        case Left(err) if err.toList.forall {
          case _ :KeyNotFound => true
          case _ => false
        } => Right(None)
        case Left(err) => Left(err)
      }
    }

  def load = loadConfig[Config]("wust")
}
