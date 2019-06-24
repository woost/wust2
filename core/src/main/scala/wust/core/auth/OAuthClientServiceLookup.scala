package wust.core.auth

import wust.core.{Server, ServerPaths}
import wust.core.config.ServerConfig
import wust.core.pushnotifications.PushClients
import wust.ids.{OAuthClientService, UserId}

import scala.concurrent.{ExecutionContext, Future}

class OAuthClientServiceLookup(jwt: JWT, serverConfig: ServerConfig, pushClient: Option[PushClients]) {
  def getAccessToken(service: OAuthClientService, code: String)(implicit ec: ExecutionContext): Option[Future[String]] = {
    service match {
      case OAuthClientService.Pushed => pushClient.flatMap(_.pushedClient.map(_.getAccessToken(code).map(_.response.data.access_token)))
    }
  }


  def getUrl(userId: UserId, service: OAuthClientService): Option[String] = {
    val token = jwt.generateOAuthClientToken(userId, OAuthClientService.Pushed)
//    val redirectUri = s"https://${serverConfig.host}/${ServerPaths.oauth}/${token.string}" // canoot use ?token= because pushed append ?code=... and not &
    val redirectUri = s"https://1c65a114.ngrok.io/${ServerPaths.oauth}/${token.string}"

    service match {
      case OAuthClientService.Pushed => pushClient.flatMap(_.pushedClient.map(_.oAuthUrl(redirectUri)))
    }
  }
}
