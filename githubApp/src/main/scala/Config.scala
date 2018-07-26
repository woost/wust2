package wust.github

import wust.sdk.{OAuthConfig, ServerConfig, WustConfig}

case class RedisConfig(host: String, port: Int)
case class Config(wust: WustConfig, server: ServerConfig, oauth: OAuthConfig, redis: RedisConfig)

object Config {
  import pureconfig._
  import wust.util.Config._

  def load = loadConfig[Config]("wust.github")
}
