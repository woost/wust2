package wust.sdk

case class WustConfig(host: String, port: Int, user: String, password: String) {
  override def toString = s"WustConfig($host, $port, $user, ***)"
}
case class ServerConfig(
                         host: String,
                         port: Int,
                         webhookPath: String,
                         allowedOrigins: List[String],
                       )

case class OAuthConfig(clientId: String,
  clientSecret: String,
  siteUri: String,
  authPath: Option[String],
  redirectUri: Option[String],
  authorizeUrl: Option[String],
  tokenUrl: Option[String],
) {
  override def toString = s"OAuthConfig(***, ***, $siteUri, $authPath, $redirectUri, $authorizeUrl, $tokenUrl)"
}

case class DefaultConfig(appServer: ServerConfig, wustServer: WustConfig, oAuth: OAuthConfig)

object Config {
  import pureconfig._
  import pureconfig.error.ConfigReaderFailures
  import wust.util.Config._

  def load(str: String): Either[ConfigReaderFailures, DefaultConfig] = loadConfig[DefaultConfig](str)
}
