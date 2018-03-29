package wust.slack

case class WustConfig(host: String, port: String, user: String, password: String) {
  override def toString: String = s"WustConfig($host, $user, ***)"
}
case class Config(accessToken: String, wust: WustConfig)

object Config {
  import pureconfig._
  import wust.utilBackend.Config._

  def load = loadConfig[Config]("wust.slack")
}
