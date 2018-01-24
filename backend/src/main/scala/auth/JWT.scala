package wust.backend.auth

import derive.derive
import pdi.jwt.{JwtCirce, JwtAlgorithm, JwtClaim}
import wust.api._, serialize.Circe._
import wust.backend.config.Config
import wust.graph.User
import wust.ids._
import scala.util.{Success, Failure}
import java.time.Instant
import scala.concurrent.duration.Duration

@derive((user, expires) => toString)
case class JWTAuthentication private[auth] (user: User.Persisted, expires: Long, token: Authentication.Token) {
  def toAuthentication = Authentication.Verified(user, token)

  def isExpired: Boolean = isExpiredIn(Duration.Zero)
  def isExpiredIn(duration: Duration): Boolean = expires <= Instant.now.getEpochSecond + duration.toSeconds
}

@derive(apply)
class JWT(secret: String, tokenLifetime: Duration) {

  private val algorithm = JwtAlgorithm.HS256
  private val issuer = "wust"
  private val audience = "wust"

  private def generateClaim(user: User.Persisted, expires: Long) = {
    JwtClaim(content = user.asJson.toString)
      .by(issuer)
      .to(audience)
      .startsNow
      .issuedNow
      .expiresAt(expires)
  }

  def generateAuthentication(user: User.Persisted): JWTAuthentication = {
    val expires = Instant.now.getEpochSecond + tokenLifetime.toSeconds
    val claim = generateClaim(user, expires)
    val token = JwtCirce.encode(claim, secret, algorithm)
    JWTAuthentication(user, expires, token)
  }

  def authenticationFromToken(token: Authentication.Token): Option[JWTAuthentication] = {
    JwtCirce.decode(token, secret, Seq(algorithm)).toOption.flatMap {
      case claim if claim.isValid(issuer, audience) => for {
        expires <- claim.expiration
        user <- parser.decode[User.Persisted](claim.content).right.toOption
      } yield JWTAuthentication(user, expires, token)
      case _ => None
    }
  }
}
