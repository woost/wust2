package wust.github

import derive.derive

@derive((host, user) => toString)
case class WustConfig(host: String, port: String, user: String, password: String)
case class GithubConfig(host: String, port: Int, path: String, accessToken: Option[String])
case class Config(wust: WustConfig, github: GithubConfig)

object Config {
  import pureconfig._
  import wust.util.Config._

  def load = loadConfig[Config]("wust.github")
}
