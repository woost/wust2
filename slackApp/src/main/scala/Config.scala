package wust.slack

import wust.sdk.{OAuthConfig, ServerConfig, WustConfig}
import com.typesafe.config.{Config => TConfig}

case class SlackClientConfig(token: String, botId: String, signingSecret: String)

case class Config(wustServer: WustConfig, appServer: ServerConfig, oAuth: OAuthConfig, postgres: TConfig, slack: SlackClientConfig)

object Config {
  import pureconfig._
  import wust.util.Config._
  import pureconfig.error.ConfigReaderFailures

  def load(path: String = "wust.slack"): Either[ConfigReaderFailures, Config] = loadConfig[Config](path)
}
