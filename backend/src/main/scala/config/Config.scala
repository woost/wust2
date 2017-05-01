package wust.backend.config

import com.typesafe.config.{Config => TConfig, ConfigFactory}
import wust.ids._

object ConfigExt {
  implicit class RichConfig(val conf: TConfig) extends AnyVal {
    def getOption[T](path: String, getter: TConfig => String => T): Option[T] = conf.hasPath(path) match {
      case true => Some(getter(conf)(path))
      case false => None
    }
  }
}

case class SmtpConfig(endpoint: String, username: String, password: String) {
  override def toString = s"SmtpConfig($endpoint, $username, ***)"
}

object Config {
  import ConfigExt._
  private val wustConfig = ConfigFactory.load.getConfig("wust")

  object usergroup {
    private val config = wustConfig.getConfig("usergroup")

    val publicId: GroupId = GroupId(config.getLong("publicId"))
  }

  object auth {
    private val config = wustConfig.getConfig("auth")

    val enableImplicit: Boolean = config.getBoolean("enableImplicit")
    val tokenLifetime: Long = config.getLong("tokenLifetimeSeconds")
    val secret: String = config.getString("secret")
  }

  object email {
    private val config = wustConfig.getConfig("email")
    private val smtpConfig = config.getConfig("smtp")

    val fromAddress: Option[String] = config.getOption("fromAddress", _.getString)
    val smtp = for {
      username <-smtpConfig.getOption("username", _.getString)
      password <- smtpConfig.getOption("password", _.getString)
      endpoint <- smtpConfig.getOption("endpoint", _.getString)
    } yield SmtpConfig(endpoint, username, password)
  }
}
