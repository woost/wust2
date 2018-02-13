package wust.github

import derive.derive

//@derive((host, user) => toString)
case class WustConfig(host: String, port: String, user: String, password: String)
{
  override def toString = s"WustConfig($host, $port, $user)"
}
case class ServerConfig(host: String, port: Int, webhookPath: String, authPath: String, allowedOrigins: List[String])
case class GithubConfig(accessToken: Option[String], clientId: String, clientSecret: String)
case class Config(server: ServerConfig, wust: WustConfig, github: GithubConfig)

object Config {
  import pureconfig._
  import wust.util.Config._

  def load = loadConfig[Config]("wust.github")
}
