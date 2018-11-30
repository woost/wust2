package wust.backend.auth

import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import wust.api._
import wust.api.serialize.Circe._
import wust.backend.config.Config
import wust.ids._

import scala.util.{Failure, Success, Try}
import java.time.Instant

import scala.concurrent.duration._
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import wust.backend.auth
import wust.backend.auth.JWT.CustomClaim

class JWT(secret: String, tokenLifetime: Duration) {

  private val algorithm = JwtAlgorithm.HS256
  private val issuer = "wust"
  private val audience = "wust"

  // implicit users have an endless token lifetime, because they have no password.
  // the jwt token is the only way to login as this implicit user. the token is
  // stored in e.g. localstorage on the client who can always login with this token.
  // whenever a user decides to signup or login, the content of this implicit user
  // is merged into the new login/signup user. Either way, the token will not be valid
  // afterwards and is therefore invalidated as soon as the implicit user becomes a
  // real user.
  private val implicitTokenLifeTimeSeconds = 1000 * 365 * 24 * 60 * 60 //1000 years, TODO: configure

  private def generateClaim(custom: CustomClaim, userId: UserId, expires: Long) = {
    JwtClaim(content = custom.asJson.noSpaces, subject = Some(userId.toBase58))
      .by(issuer)
      .to(audience)
      .startsNow
      .issuedNow
      .expiresAt(expires)
  }

  def generateEmailActivationToken(userId: UserId, email: String): Authentication.Token = {
    val thisTokenLifetimeSeconds: Long = 24 * 60 * 60 // 24 hours, TODO: configure
    val expires = Instant.now.getEpochSecond + thisTokenLifetimeSeconds
    val claim = generateClaim(CustomClaim.EmailVerify(userId, email), userId, expires)
    val token = JwtCirce.encode(claim, secret, algorithm)
    token
  }

  def emailActivationFromToken(token: Authentication.Token): Option[VerifiedEmailActivationToken] = {
    JwtCirce.decode(token, secret, Seq(algorithm)).toOption.flatMap {
      case claim if claim.isValid(issuer, audience) =>
        for {
          expires <- claim.expiration
          emailVerify <- parser.decode[CustomClaim](claim.content)
            .right.toOption.collect { case verify: CustomClaim.EmailVerify => verify }
          subject <- claim.subject.flatMap(str => Try(UserId(NodeId(Cuid.fromBase58(str)))).toOption) // TODO: proper failable fromBase58
          if emailVerify.userId == subject
        } yield auth.VerifiedEmailActivationToken(emailVerify.userId, emailVerify.email, expires)
      case _ => None
    }
  }

  def generateAuthentication(user: AuthUser.Persisted): Authentication.Verified = {
    val thisTokenLifetimeSeconds: Long = user match {
      case _: AuthUser.Real     => tokenLifetime.toSeconds
      case _: AuthUser.Implicit => implicitTokenLifeTimeSeconds
    }

    val expires = Instant.now.getEpochSecond + thisTokenLifetimeSeconds
    val claim = generateClaim(CustomClaim.UserAuth(user), user.id, expires)
    val token = JwtCirce.encode(claim, secret, algorithm)
    Authentication.Verified(user, expires, token)
  }

  def authenticationFromToken(token: Authentication.Token): Option[Authentication.Verified] = {
    JwtCirce.decode(token, secret, Seq(algorithm)).toOption.flatMap {
      case claim if claim.isValid(issuer, audience) =>
        for {
          expires <- claim.expiration
          user <- parser.decode[CustomClaim](claim.content)
            .right.toOption.collect { case CustomClaim.UserAuth(user) => user }
          subject <- claim.subject.flatMap(str => Try(UserId(NodeId(Cuid.fromBase58(str)))).toOption) // TODO: proper failable fromBase58
          if user.id == subject
        } yield Authentication.Verified(user, expires, token)
      case _ => None
    }
  }
}
object JWT {
  def isExpired(auth: Authentication.Verified): Boolean = auth.expires <= Instant.now.getEpochSecond
  def isExpired(auth: VerifiedEmailActivationToken): Boolean = auth.expires <= Instant.now.getEpochSecond

  sealed trait CustomClaim
  object CustomClaim {
    case class UserAuth(user: AuthUser.Persisted) extends CustomClaim
    case class EmailVerify(userId: UserId, email: String) extends CustomClaim

    import io.circe._, io.circe.generic.extras.semiauto._
    implicit val decoder: Decoder[CustomClaim] = deriveDecoder[CustomClaim]
    implicit val encoder: Encoder[CustomClaim] = deriveEncoder[CustomClaim]
  }
}

case class VerifiedEmailActivationToken(userId: UserId, email: String, expires: Long)
