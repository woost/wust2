package wust.core

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import wust.api.Authentication
import wust.core.auth.JWT
import wust.core.config.ServerConfig
import wust.db.Db

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class EmailVerificationEndpoint(db: Db, jwt: JWT, config: ServerConfig) {
  import akka.http.scaladsl.server.Directives._

  def verify(token: Authentication.Token)(implicit ec: ExecutionContext): Route = {
    def link =  s"""<a href="https://${config.host}">Go back to app</a>"""
    def successMessage = complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Your email address has been verified. Thank you! $link"))
    def invalidMessage = complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Cannot verify email address. This email verification token was already used or is invalid or expired. $link"))
    def errorMessage = complete(StatusCodes.InternalServerError -> s"Sorry, we cannot verify your email address. Please try again later.")

    jwt.emailActivationFromToken(token) match {
      case Some(activation) =>
        onComplete(db.user.verifyEmailAddress(activation.userId, activation.email)) {
          case Success(true) => successMessage
          case Success(false) => invalidMessage
          case Failure(t) =>
            scribe.error("There was an error when verifying an email address", t)
            errorMessage
        }
      case _ => invalidMessage
    }
  }

}
