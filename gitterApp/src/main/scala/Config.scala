package wust.gitter

//@derive((host, user) => toString)
case class WustConfig(host: String, port: String, user: String, password: String) { //TODO put in sdk or util?
  override def toString = s"WustConfig($host, $port, $user, ***)"
}
case class Config(accessToken: String, wust: WustConfig)

object Config {
  import pureconfig._
  import wust.utilBackend.Config._

  def load = loadConfig[Config]("wust.gitter")
}
