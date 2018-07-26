package wust.sdk

import java.net.URI
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, Materializer}
import com.github.dakatsuka.akka.http.oauth2.client.{AccessToken, GrantType, Client => AuthClient, Config => AuthConfig}
import com.github.dakatsuka.akka.http.oauth2.client.Error.UnauthorizedException
import com.github.dakatsuka.akka.http.oauth2.client.strategy._
import monix.reactive.Observer
import wust.ids.UserId

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

// Instantiate for each App
case class OAuthClient(oAuthConfig: OAuthConfig, serverConfig: ServerConfig)(implicit val system: ActorSystem, implicit val ec: ExecutionContext, implicit val mat: Materializer) {

  val oAuthRequests: TrieMap[String, UserId] = TrieMap.empty[String, UserId]

  private val authConfig = AuthConfig(
    clientId     = oAuthConfig.clientId,
    clientSecret = oAuthConfig.clientSecret,
    site         = URI.create(oAuthConfig.siteUri),
    authorizeUrl = oAuthConfig.authorizeUrl.getOrElse("/oauth/authorize"),
    tokenUrl     = oAuthConfig.tokenUrl.getOrElse("/oauth/token")
  )

  private val authClient = AuthClient(authConfig)

  def authorizeUrl(userId: UserId, params: Map[String, String] = Map.empty[String, String]): Option[Uri] = {

    val randomState = UUID.randomUUID().toString
    val uri = authClient.getAuthorizeUrl(GrantType.AuthorizationCode, Map(
      //    "redirect_uri" -> s"http://${serverConfig.host}:${serverConfig.port}/${oAuthConfig.authPath}",
      "redirect_uri" -> s"http://088eb7a9.ngrok.io/oauth/auth",
      "state" -> randomState,
      "scopes" -> List("read:org", "read:user", "repo", "write:discussion").mkString(",")
    ) ++ params)

    oAuthRequests.putIfAbsent(randomState, userId) match {
      case None => uri
      case _ =>
        scribe.error("Duplicate state in url generation")
        None
    }
  }

  private def confirmOAuthRequest(code: String, state: String): Boolean = {
    val currRequest = oAuthRequests.get(state) match {
      case Some(_) => true
      case _ =>
        scribe.error(s"Could not confirm oAuthRequest. No such request in queue")
        false
    }
    code.nonEmpty && currRequest
  }

  //val newAccessToken: Future[Either[Throwable, AccessToken]] =
  //  client.getAccessToken(GrantType.RefreshToken, Map("refresh_token" -> "zzzzzzzz"))

  def route(tokenObserver: Observer.Sync[AccessToken]): Route = path(separateOnSlashes(oAuthConfig.authPath)) {
    get {
      parameters(('code, 'state)) { (code: String, state: String) =>
        if (confirmOAuthRequest(code, state)) {

          val accessToken: Future[Either[Throwable, AccessToken]] = authClient.getAccessToken(
            grant = GrantType.AuthorizationCode,
            params = Map(
              "code" -> code,
              // "redirect_uri" -> s"http://${serverConfig.host}:${serverConfig.port}/${oAuthConfig.authPath}",
              "redirect_uri" -> s"http://088eb7a9.ngrok.io/oauth/auth",
              "state" -> state
            )
          )

          accessToken.foreach {
            case Right(t) =>
              tokenObserver.onNext(t)
              oAuthRequests.remove(state)
            case Left(ex: UnauthorizedException) =>
              scribe.error(s"unauthorized error receiving access token: $ex")
          }

        } else {
          scribe.error(s"Could not verify request(code, state): ($code, $state)")
        }
        redirect(s"http://${serverConfig.host}:12345/#view=usersettings&page=default", StatusCodes.SeeOther) //TODO: necessary or is is sufficient to set redirect uri above?
      }
    }
  }
}

object OAuthClient {
  implicit val system: ActorSystem  = ActorSystem()
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer    = ActorMaterializer()

  def create(oAuthConfig: OAuthConfig, server: ServerConfig): OAuthClient = {
    new OAuthClient(oAuthConfig, server)
  }

}
