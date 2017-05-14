package wust.config

import autoconfig.config
import com.typesafe.config.{Config => TConfig}
import java.time.Duration

//@derive((endpoint, username) => toString)
case class SmtpConfig(endpoint: String, username: String, password: String)
case class AuthConfig(enableImplicit: Boolean, tokenLifetime: Duration, secret: String)
case class EmailConfig(fromAddress: String, smtp: SmtpConfig)

@config
object StageConfig {
  val stage: String
  val section = s"stages.${stage}"
}

@config(section = StageConfig.section)
object Config  {
import com.typesafe.config.Config
  val auth: AuthConfig
  val email: Option[EmailConfig]
  val db: TConfig
}
