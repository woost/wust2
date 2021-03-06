package wust.github

import wust.sdk.{OAuthConfig, ServerConfig, WustConfig}

final case class RedisConfig(host: String, port: Int)
final case class Config(wustServer: WustConfig, appServer: ServerConfig, oAuth: OAuthConfig, redis: RedisConfig)

object Config {
  import pureconfig._
  import wust.util.Config._

  def load = loadConfig[Config]("wust.github")
}
