package wust.core.auth

import java.time.Instant

import io.circe._
import io.circe.syntax._
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import wust.api._
import wust.api.serialize.Circe._
import wust.ids._

import scala.concurrent.duration._

class JWT(secret: String, tokenLifetime: Duration) {
  import JWT.CustomClaim

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
  val implicitTokenLifeTimeSeconds = 1000 * 365 * 24 * 60 * 60 //1000 years, TODO: configure
  val emailVerificationTokenLifeTimeSeconds = 24 * 60 * 60 // 24 hours, TODO: configure
  val passwordResetTokenLifeTimeSeconds = 1 * 60 * 60 // 1 hour, TODO: configure
  val redirectOAuthTokenLifeTimeSeconds = 5 * 60 // 5 minutes, TODO: configure

  private def generateClaim(custom: CustomClaim, userId: UserId, expires: Long) = {
    //TODO we are writing content into the root of the object, we should put our custom claim object into a field. Or should we not do this in jwt?
    JwtClaim(content = custom.asJson.noSpaces, subject = Some(userId.toBase58))
      .by(issuer)
      .to(audience)
      .startsNow
      .issuedNow
      .expiresAt(expires)
  }

  def generateEmailActivationToken(userId: UserId, email: String): Authentication.Token = {
    val thisTokenLifetimeSeconds: Long = emailVerificationTokenLifeTimeSeconds
    val expires = Instant.now.getEpochSecond + thisTokenLifetimeSeconds
    val claim = generateClaim(CustomClaim.EmailVerify(userId, email), userId, expires)
    val token = JwtCirce.encode(claim, secret, algorithm)
    Authentication.Token(token)
  }

  def emailActivationFromToken(token: Authentication.Token): Option[VerifiedEmailActivationToken] = {
    JwtCirce.decode(token.string, secret, Seq(algorithm)).toOption.flatMap {
      case claim if claim.isValid(issuer, audience) =>
        for {
          verified <- parser.decode[CustomClaim](claim.content)
            .right.toOption.collect { case verify: CustomClaim.EmailVerify => VerifiedEmailActivationToken(verify.userId, verify.email) }
          subject <- claim.subject.flatMap(str => Cuid.fromBase58String(str).map(id => UserId(NodeId(id))).toOption)
          if verified.userId == subject
        } yield verified
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
    Authentication.Verified(user, expires, Authentication.Token(token))
  }

  def authenticationFromToken(token: Authentication.Token): Option[Authentication.Verified] = {
    JwtCirce.decode(token.string, secret, Seq(algorithm)).toOption.flatMap {
      case claim if claim.isValid(issuer, audience) =>
        for {
          expires <- claim.expiration
          verified <- parser.decode[CustomClaim](claim.content)
            .right.toOption.collect { case CustomClaim.UserAuth(user) => Authentication.Verified(user, expires, token) }
          subject <- claim.subject.flatMap(str => Cuid.fromBase58String(str).map(id => UserId(NodeId(id))).toOption)
          if verified.user.id == subject
        } yield verified
      case _ => None
    }
  }

  def generatePasswordResetToken(user: AuthUser.Persisted): Authentication.Token = {
    val thisTokenLifetimeSeconds: Long = passwordResetTokenLifeTimeSeconds
    val expires = Instant.now.getEpochSecond + thisTokenLifetimeSeconds
    val claim = generateClaim(CustomClaim.PasswordReset(user), user.id, expires)
    val token = JwtCirce.encode(claim, secret, algorithm)
    Authentication.Token(token)
  }

  def passwordResetUserFromToken(token: Authentication.Token): Option[AuthUser.Persisted] = {
    JwtCirce.decode(token.string, secret, Seq(algorithm)).toOption.flatMap {
      case claim if claim.isValid(issuer, audience) =>
        for {
          expires <- claim.expiration
          verified <- parser.decode[CustomClaim](claim.content)
            .right.toOption.collect { case CustomClaim.PasswordReset(user) => user }
          subject <- claim.subject.flatMap(str => Cuid.fromBase58String(str).map(id => UserId(NodeId(id))).toOption)
          if verified.user.id == subject
        } yield verified
      case _ => None
    }
  }

  def generateInvitationToken(user: AuthUser.Implicit): Authentication.Token = {
    val thisTokenLifetimeSeconds: Long = implicitTokenLifeTimeSeconds

    val expires = Instant.now.getEpochSecond + thisTokenLifetimeSeconds
    val claim = generateClaim(CustomClaim.Invitation(user), user.id, expires)
    val token = JwtCirce.encode(claim, secret, algorithm)
    Authentication.Token(token)
  }

  def invitationUserFromToken(token: Authentication.Token): Option[AuthUser.Implicit] = {
    JwtCirce.decode(token.string, secret, Seq(algorithm)).toOption.flatMap {
      case claim if claim.isValid(issuer, audience) =>
        for {
          user <- parser.decode[CustomClaim](claim.content)
            .right.toOption.collect { case CustomClaim.Invitation(user) => user }
          subject <- claim.subject.flatMap(str => Cuid.fromBase58String(str).map(id => UserId(NodeId(id))).toOption)
          if user.id == subject
        } yield user
      case _ => None
    }
  }

  def generateOAuthClientToken(userId: UserId, service: OAuthClientService): Authentication.Token = {
    val thisTokenLifetimeSeconds: Long = redirectOAuthTokenLifeTimeSeconds

    val expires = Instant.now.getEpochSecond + thisTokenLifetimeSeconds
    val claim = generateClaim(CustomClaim.OAuthClient(userId, service), userId, expires)
    val token = JwtCirce.encode(claim, secret, algorithm)
    Authentication.Token(token)
  }

  def oAuthClientFromToken(token: Authentication.Token): Option[VerifiedOAuthClientToken] = {
    JwtCirce.decode(token.string, secret, Seq(algorithm)).toOption.flatMap {
      case claim if claim.isValid(issuer, audience) =>
        for {
          verifiedToken <- parser.decode[CustomClaim](claim.content)
            .right.toOption.collect { case CustomClaim.OAuthClient(userId, service) => VerifiedOAuthClientToken(userId, service) }
          subject <- claim.subject.flatMap(str => Cuid.fromBase58String(str).map(id => UserId(NodeId(id))).toOption)
          if verifiedToken.userId == subject
        } yield verifiedToken
      case _ => None
    }
  }
}
object JWT {
  def isExpired(auth: Authentication.Verified): Boolean = auth.expires <= Instant.now.getEpochSecond

  sealed trait CustomClaim
  object CustomClaim {
    final case class UserAuth(user: AuthUser.Persisted) extends CustomClaim
    final case class Invitation(user: AuthUser.Implicit) extends CustomClaim
    final case class PasswordReset(user: AuthUser.Persisted) extends CustomClaim
    final case class EmailVerify(userId: UserId, email: String) extends CustomClaim
    final case class OAuthClient(userId: UserId, serviceIdentifier: OAuthClientService) extends CustomClaim

    import io.circe._
    import io.circe.generic.extras.auto._
    import io.circe.generic.extras.semiauto._
    implicit val decoder: Decoder[CustomClaim] = deriveDecoder[CustomClaim]
    implicit val encoder: Encoder[CustomClaim] = deriveEncoder[CustomClaim]
  }
}

final case class VerifiedEmailActivationToken(userId: UserId, email: String)
final case class VerifiedOAuthClientToken(userId: UserId, service: OAuthClientService)
