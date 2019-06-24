package wust.core

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import wust.api.Authentication
import wust.core.auth.{JWT, OAuthClientServiceLookup}
import wust.core.config.ServerConfig
import wust.core.pushnotifications.PushClients
import wust.db.{Data, Db}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class OAuthClientVerificationEndpoint(db: Db, jwt: JWT, config: ServerConfig, serviceLookup: OAuthClientServiceLookup) {
  import akka.http.scaladsl.server.Directives._

  def verify(token: Authentication.Token, code: String)(implicit ec: ExecutionContext, materializer: ActorMaterializer): Route = {
    val linkUrl = s"https://${config.host}/#user-settings"
    def link =  s"""<a href="$linkUrl">Go back to app</a>"""
    def successMessage = redirect(Uri(linkUrl), StatusCodes.TemporaryRedirect)
    def invalidMessage = complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Cannot verify OAuth flow. This token was already used or is invalid or expired. $link"))
    def errorMessage = complete(StatusCodes.InternalServerError -> s"Sorry, we cannot verify the OAuth flow right now. Please try again later.")

    scribe.info(s"Verifying OAuth Client callback: $token, $code")
    jwt.oAuthClientFromToken(token) match {
      case Some(activation) =>
        scribe.error("Token is readable")
        serviceLookup.getAccessToken(activation.service, code) match {
          case Some(accessTokenFuture) =>
            onComplete(accessTokenFuture.flatMap(token => db.oAuthClients.create(Data.OAuthClient(activation.userId, service = activation.service, accessToken = token)))) {
              case Success(true) => successMessage
              case Success(false) => invalidMessage
              case Failure(t) => errorMessage
            }
          case None => invalidMessage
        }
      case _ => invalidMessage
    }
  }
}
