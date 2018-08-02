package wust.sdk

import java.net.URI
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, Materializer}
import com.github.dakatsuka.akka.http.oauth2.client.{GrantType, AccessToken => OAuthToken, Client => AuthClient, Config => AuthConfig}
import com.github.dakatsuka.akka.http.oauth2.client.Error.UnauthorizedException
import com.github.dakatsuka.akka.http.oauth2.client.strategy._
import monix.reactive.Observer
import wust.api.Authentication
import wust.ids.UserId

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

case class AuthenticationData(wustAuthData: Authentication.Verified, platformAuthToken: OAuthToken)

// Instantiate for each App
class OAuthClient(val oAuthConfig: OAuthConfig, serverConfig: ServerConfig)(implicit val system: ActorSystem, implicit val ec: ExecutionContext, implicit val mat: Materializer) {

  val oAuthRequests: TrieMap[String, Authentication.Verified] = TrieMap.empty[String, Authentication.Verified]

  scribe.info(s"${oAuthConfig.toString}")
  private val authConfig = AuthConfig(
    clientId     = oAuthConfig.clientId,
    clientSecret = oAuthConfig.clientSecret,
    site         = URI.create(oAuthConfig.siteUri),
    authorizeUrl = oAuthConfig.authorizeUrl.getOrElse("/oauth/authorize"),
    tokenUrl     = oAuthConfig.tokenUrl.getOrElse("/oauth/token")
  )

  private val authClient = AuthClient(authConfig)
  private val oAuthPath = oAuthConfig.authPath.getOrElse("oauth/auth")
  private val redirectUri = oAuthConfig.redirectUri.getOrElse(s"http://${serverConfig.host}:${serverConfig.port}/") + oAuthPath


  def authorizeUrl(auth: Authentication.Verified, scope: List[String], params: Map[String, String] = Map.empty[String, String]): Option[Uri] = {

    val randomState = UUID.randomUUID().toString
    val uri = authClient.getAuthorizeUrl(GrantType.AuthorizationCode, params ++
      Map(
        "redirect_uri" -> redirectUri,
        "state" -> randomState,
        "scope" -> scope.mkString(",")
      )
    )

    oAuthRequests.putIfAbsent(randomState, auth) match {
      case None => uri
      case _ =>
        scribe.error("Duplicate state in url generation")
        None
    }
  }

  private def confirmOAuthRequest(code: String, state: String): Option[Authentication.Verified] = {
    val currRequest = oAuthRequests.get(state)
    currRequest match {
      case Some(v: Authentication.Verified) if code.nonEmpty => Some(v)
      case _ =>
        scribe.error(s"Could not confirm oAuthRequest. No such request in queue")
        None
    }
  }

  //val newAccessToken: Future[Either[Throwable, OAuthToken]] =
  //  client.getAccessToken(GrantType.RefreshToken, Map("refresh_token" -> "zzzzzzzz"))

  def route(tokenObserver: Observer[AuthenticationData]): Route = path(separateOnSlashes(oAuthPath)) {
    get {
      parameters(('code, 'state)) { (code: String, state: String) =>
        val confirmedRequest = confirmOAuthRequest(code, state)
        if (confirmedRequest.isDefined) {

          val accessToken: Future[Either[Throwable, OAuthToken]] = authClient.getAccessToken(
            grant = GrantType.AuthorizationCode,
            params = Map(
              "code" -> code,
              "redirect_uri" -> redirectUri,
              "state" -> state
            )
          )

          accessToken.foreach {
            case Right(t) =>
              tokenObserver.onNext(AuthenticationData(confirmedRequest.get, t))
              oAuthRequests.remove(state)
            case Left(ex: UnauthorizedException) =>
              scribe.error(s"unauthorized error receiving access token: $ex")
            case ex =>
              scribe.error(s"unknown error receiving access token: $ex")
          }

        } else {
          scribe.error(s"Could not verify request(code, state): ($code, $state)")
        }
        redirect(s"http://${serverConfig.host}:12345/#view=usersettings&page=default", StatusCodes.SeeOther) //TODO: necessary or is is sufficient to set redirect uri above?
      }
      // TODO: handle user aborts
    }
  }
}

object OAuthClient {
  implicit val system: ActorSystem  = ActorSystem()
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer    = ActorMaterializer()

  def apply(oAuthConfig: OAuthConfig, server: ServerConfig): OAuthClient = {
    new OAuthClient(oAuthConfig, server)
  }
}
