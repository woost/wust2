package wust.sdk

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.collection.immutable

object CorsSupport {
  def check(allowedOrigins: HttpOriginRange.Default)(inner: Route): Route =
    checkSameOrigin(allowedOrigins) {
      headerValueByType[Origin]() { origin =>
        respondWithHeaders(corsHeaders(origin.origins)) {
          inner
        }
      }
    }

  private def corsHeaders(allowedOrigins: immutable.Seq[HttpOrigin]) = immutable.Seq(
    `Access-Control-Allow-Origin`(HttpOriginRange.Default(allowedOrigins))
  )
}
