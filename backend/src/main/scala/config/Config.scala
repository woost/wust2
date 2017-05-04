package wust.backend.config

import autoconfig.config
import wust.ids._

//@derive((endpoint, username) => toString)
case class SmtpConfig(endpoint: String, username: String, password: String)
case class AuthConfig(enableImplicit: Boolean, tokenLifetime: Long, secret: String) //TODO: tokenLifetime -> Duration
case class EmailConfig(fromAddress: String, smtp: SmtpConfig)

@config(section = wust)
object Config {
  val auth: AuthConfig
  val email: Option[EmailConfig]
}
