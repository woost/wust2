package wust.github

case class WustConfig(host: String, port: String, user: String, password: String) {
  override def toString = s"WustConfig($host, $port, $user, ***)"
}
case class ServerConfig(
    host: String,
    port: Int,
    webhookPath: String,
    authPath: String,
    allowedOrigins: List[String]
)
case class GithubConfig(accessToken: Option[String], clientId: String, clientSecret: String)
case class RedisConfig(host: String, port: Int)
case class Config(server: ServerConfig, wust: WustConfig, github: GithubConfig, redis: RedisConfig)

object Config {
  import pureconfig._
  import wust.utilBackend.Config._

  def load = loadConfig[Config]("wust.github")
}
