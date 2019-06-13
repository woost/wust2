package wust.slack

import com.typesafe.config.{Config => TConfig}
import wust.sdk.{OAuthConfig, ServerConfig, WustConfig}
import wust.serviceUtil.Logging

final case class SlackClientConfig(token: String, botId: String, signingSecret: String) {
  override def toString = "SlackClientConfig(***, ***, ***)"
}

final case class Config(wustServer: WustConfig, appServer: ServerConfig, oAuth: OAuthConfig, postgres: TConfig, slack: SlackClientConfig, logstash: Option[Logging.LogstashConfig])

object Config {
  import pureconfig._
  import pureconfig.error.ConfigReaderFailures
  import wust.util.Config._

  def load(path: String = "wust.slack"): Either[ConfigReaderFailures, Config] = loadConfig[Config](path)
}
