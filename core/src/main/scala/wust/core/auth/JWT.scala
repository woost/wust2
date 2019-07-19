package wust.core.auth

import java.time.Instant

import io.circe._
import io.circe.syntax._
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import wust.api._
import wust.api.serialize.Circe._
import wust.ids._

import scala.concurrent.duration._
import scala.reflect.ClassTag

class JWT(secret: String, authTokenLifeTime: Duration) {
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
  // TODO: configure in config
  object LifeTimeSeconds {
    val implicitUserAuth = 1000 * 365 * 24 * 60 * 60 // 1000 years
    val realUserAuth = authTokenLifeTime.toSeconds // configured
    val invitation = 1 * 365 * 24 * 60 * 60 // 1 year
    val emailActivation = 24 * 60 * 60 // 24 hours
    val passwordReset = 1 * 60 * 60 // 1 hour
    val redirectOAuth = 5 * 60 // 5 minutes
  }

  private def generateClaim(custom: CustomClaim, expires: Long) = {
    //TODO we are writing content into the root of the object, we should put our custom claim object into a field. Or should we not do this in jwt?
    JwtClaim(content = custom.asJson.noSpaces, subject = Some(custom.userId.toBase58))
      .by(issuer)
      .to(audience)
      .startsNow
      .issuedNow
      .expiresAt(expires)
  }

  private def generateTokenWithExpiration(custom: CustomClaim, thisTokenLifetimeSeconds: Long): (Authentication.Token, Long)  = {
    val expires = Instant.now.getEpochSecond + thisTokenLifetimeSeconds
    val claim = generateClaim(custom, expires)
    val token = JwtCirce.encode(claim, secret, algorithm)
    (Authentication.Token(token), expires)
  }

  private def verifiedClaimFromToken[Claim <: CustomClaim : ClassTag](token: Authentication.Token): Option[(Claim, Long)] = {
    JwtCirce.decode(token.string, secret, Seq(algorithm)).toOption.flatMap {
      case claim if claim.isValid(issuer, audience) =>
        for {
          expires <- claim.expiration
          if expires > Instant.now.getEpochSecond // isValid already checks this, but let's be explicit
          verified <- parser.decode[CustomClaim](claim.content)
            .right.toOption.collect { case verify: Claim => verify }
          subject <- claim.subject.flatMap(str => Cuid.fromBase58String(str).map(id => UserId(NodeId(id))).toOption)
          if verified.userId == subject
        } yield (verified, expires)
      case _ => None
    }
  }

  def generateEmailActivationToken(userId: UserId, email: String): Authentication.Token = {
    val (token, _) = generateTokenWithExpiration(CustomClaim.EmailVerify(userId, email), LifeTimeSeconds.emailActivation)
    token
  }

  def emailActivationFromToken(token: Authentication.Token): Option[VerifiedEmailActivationToken] = {
    verifiedClaimFromToken[CustomClaim.EmailVerify](token)
      .map { case (verify, _) => VerifiedEmailActivationToken(verify.userId, verify.email) }
  }

  def generateAuthentication(user: AuthUser.Persisted): Authentication.Verified = {
    val thisTokenLifetimeSeconds: Long = user match {
      case _: AuthUser.Real     => LifeTimeSeconds.realUserAuth
      case _: AuthUser.Implicit => LifeTimeSeconds.implicitUserAuth
    }
    val (token, expires) = generateTokenWithExpiration(CustomClaim.UserAuth(user), thisTokenLifetimeSeconds)
    Authentication.Verified(user, expires, token)
  }

  def authenticationFromToken(token: Authentication.Token): Option[Authentication.Verified] = {
    verifiedClaimFromToken[CustomClaim.UserAuth](token)
      .map { case (verify, expires) => Authentication.Verified(verify.user, expires, token) }
  }

  def generatePasswordResetToken(user: AuthUser.Persisted): Authentication.Token = {
    val (token, _) = generateTokenWithExpiration(CustomClaim.PasswordReset(user), LifeTimeSeconds.passwordReset)
    token
  }

  def passwordResetUserFromToken(token: Authentication.Token): Option[AuthUser.Persisted] = {
    verifiedClaimFromToken[CustomClaim.PasswordReset](token)
      .map { case (verify, _) => verify.user }
  }

  def generateInvitationToken(user: AuthUser.Implicit): Authentication.Token = {
    val (token, _) = generateTokenWithExpiration(CustomClaim.Invitation(user), LifeTimeSeconds.invitation)
    token
  }

  def invitationUserFromToken(token: Authentication.Token): Option[AuthUser.Implicit] = {
    verifiedClaimFromToken[CustomClaim.Invitation](token)
      .map { case (verify, _) => verify.user }
  }

  def generateOAuthClientToken(userId: UserId, service: OAuthClientService): Authentication.Token = {
    val (token, _) = generateTokenWithExpiration(CustomClaim.OAuthClient(userId, service), LifeTimeSeconds.redirectOAuth)
    token
  }

  def oAuthClientFromToken(token: Authentication.Token): Option[VerifiedOAuthClientToken] = {
    verifiedClaimFromToken[CustomClaim.OAuthClient](token)
      .map { case (verify, _) => VerifiedOAuthClientToken(verify.userId, verify.serviceIdentifier) }
  }
}
object JWT {
  def isExpired(auth: Authentication.Verified): Boolean = auth.expires <= Instant.now.getEpochSecond

  sealed trait CustomClaim {
    def userId: UserId
  }
  object CustomClaim {
    final case class UserAuth(user: AuthUser.Persisted) extends CustomClaim {
      def userId = user.id
    }
    final case class Invitation(user: AuthUser.Implicit) extends CustomClaim {
      def userId = user.id
    }
    final case class PasswordReset(user: AuthUser.Persisted) extends CustomClaim {
      def userId = user.id
    }
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
