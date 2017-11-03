package wust.slack

import derive.derive

@derive((host, user) => toString)
case class WustConfig(host: String, user: String, password: String)
case class Config(accessToken: String, wust: WustConfig)

object Config {
  import pureconfig._

  def load = loadConfig[Config]("wust.slack")
}
