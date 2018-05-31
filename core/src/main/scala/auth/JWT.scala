package wust.backend.auth

import pdi.jwt.{JwtCirce, JwtAlgorithm, JwtClaim}
import wust.api._, wust.api.serialize.Circe._
import wust.backend.config.Config
import wust.ids._
import scala.util.{Success, Failure}
import java.time.Instant
import scala.concurrent.duration.Duration
import io.circe._, io.circe.syntax._, io.circe.parser._

class JWT(secret: String, tokenLifetime: Duration) {

  private val algorithm = JwtAlgorithm.HS256
  private val issuer = "wust"
  private val audience = "wust"

  private def generateClaim(user: AuthUser.Persisted, expires: Long) = {
    JwtClaim(content = user.asJson.toString)
      .by(issuer)
      .to(audience)
      .startsNow
      .issuedNow
      .expiresAt(expires)
  }

  def generateAuthentication(user: AuthUser.Persisted): Authentication.Verified = {
    val expires = Instant.now.getEpochSecond + tokenLifetime.toSeconds
    val claim = generateClaim(user, expires)
    val token = JwtCirce.encode(claim, secret, algorithm)
    Authentication.Verified(user, expires, token)
  }

  def authenticationFromToken(token: Authentication.Token): Option[Authentication.Verified] = {
    JwtCirce.decode(token, secret, Seq(algorithm)).toOption.flatMap {
      case claim if claim.isValid(issuer, audience) => for {
        expires <- claim.expiration
        user <- parser.decode[AuthUser.Persisted](claim.content).right.toOption
      } yield Authentication.Verified(user, expires, token)
      case _ => None
    }
  }
}
object JWT {
  def isExpired(auth: Authentication.Verified): Boolean = auth.expires <= Instant.now.getEpochSecond
}
