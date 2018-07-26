package wust.sdk

case class WustConfig(host: String, port: String, user: String, password: String) {
  override def toString = s"WustConfig($host, $port, $user, ***)"
}
case class ServerConfig(
                         host: String,
                         port: Int,
                         webhookPath: String,
                         allowedOrigins: List[String]
                       )

case class OAuthConfig(authPath: String, clientId: String, clientSecret: String, siteUri: String, authorizeUrl: Option[String], tokenUrl: Option[String])

case class DefaultConfig(server: ServerConfig, wust: WustConfig, oauth: OAuthConfig)

object Config {
  import pureconfig._
  import wust.util.Config._

  def load(str: String) = loadConfig[DefaultConfig](str)
}
