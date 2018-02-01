package wust.gitter

import derive.derive

@derive((host, user) => toString)
case class WustConfig(host: String, port: String, user: String, password: String)
case class Config(accessToken: String, wust: WustConfig)

object Config {
  import pureconfig._
  import wust.util.Config._

  def load = loadConfig[Config]("wust.gitter")
}
