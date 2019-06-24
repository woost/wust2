package wust.core.pushnotifications

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import wust.api.Authentication
import wust.core.config.PushedConfig
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// https://about.pushed.co/docs/api
class PushedClient private(val config: PushedConfig)(implicit system: ActorSystem, materializer: ActorMaterializer) {
  import PushedClient.AccessTokenResponse

  private val apiHost = "pushed.co"
  private val apiUrl = s"https://api.${apiHost}/1"

  def oAuthUrl(redirectUri: String): String = s"${apiUrl}/oauth?client_id=${config.keys.appKey}&redirect_uri=${redirectUri}"


  def getAccessToken(code: String)(implicit ec: ExecutionContext): Future[AccessTokenResponse] = {
    Http().singleRequest(HttpRequest(
      HttpMethods.POST,
      Uri(s"${ apiUrl }/oauth/access_token"),
      entity = FormData(
        FormData.BodyPart.Strict("code", code)
      ).toEntity
    )).flatMap { response =>
      Unmarshal(response.entity).to[String].flatMap { responseBody =>
        decode[AccessTokenResponse](responseBody) match {
          case Right(response) => Future.successful(response)
          case Left(err) => Future.failed(new Exception(s"Cannot decode access_token from ${apiHost}: $err"))
        }
      }
    }
  }

  def sendPush(accessToken: String, content: String, url: String)(implicit ec: ExecutionContext): Future[Int] =
    Http().singleRequest(HttpRequest(
      HttpMethods.POST,
      Uri(s"${apiUrl}/push"),
      entity = FormData(
        FormData.BodyPart.Strict("app_key", config.keys.appKey),
        FormData.BodyPart.Strict("app_secret", config.keys.appSecret),
        FormData.BodyPart.Strict("access_token", accessToken.dropRight(1)+"1"),
        FormData.BodyPart.Strict("target_type", "user"),
        FormData.BodyPart.Strict("content", content),
        FormData.BodyPart.Strict("content_type", "url"),
        FormData.BodyPart.Strict("content_extra", url),
      ).toEntity
    )).map { response =>
      response.discardEntityBytes()
      response.status.intValue
    }

}

object PushedClient {
  def apply(config: PushedConfig)(implicit system: ActorSystem, materializer: ActorMaterializer): PushedClient = new PushedClient(config)

  case class AccessTokenResponse(response: AppAuthorization)
  case class AppAuthorization(`type`: String, message: String, data: AppAuthorizationData)
  case class AppAuthorizationData(access_token: String)
}
