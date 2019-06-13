package wust.gitter

//@derive((host, user) => toString)
final case class WustConfig(host: String, port: Int, user: String, password: String) { //TODO put in sdk or util?
  override def toString = s"WustConfig($host, $port, $user, ***)"
}
final case class Config(accessToken: String, wustServer: WustConfig)

object Config {
  import pureconfig._
  import wust.util.Config._

  def load = loadConfig[Config]("wust.gitter")
}
