package wust.slack

import wust.sdk.{OAuthConfig, ServerConfig, WustConfig}
import com.typesafe.config.{Config => TConfig}

case class SlackClientConfig(token: String)

case class Config(wust: WustConfig, server: ServerConfig, oauth: OAuthConfig, postgres: TConfig, slack: SlackClientConfig)

object Config {
  import pureconfig._
  import wust.util.Config._

  def load = loadConfig[Config]("wust.slack")
}
