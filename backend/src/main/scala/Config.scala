package wust.backend

import com.typesafe.config.ConfigFactory

object Config {
  private val wustConfig = ConfigFactory.load.getConfig("wust")

  object auth {
    private val config = wustConfig.getConfig("auth")

    val enableImplicit: Boolean = config.getBoolean("enableImplicit")
    val tokenLifetime: Long = config.getLong("tokenLifetimeSeconds")
    val secret: String = config.getString("secret")
  }
}
