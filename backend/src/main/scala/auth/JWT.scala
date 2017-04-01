package wust.backend.auth

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import io.igl.jwt._

import wust.api._

object Claims {
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit val userFormat = (
    (__ \ "id").format[Long] ~
    (__ \ "name").format[String] ~
    (__ \ "isImplicit").format[Boolean] ~
    (__ \ "revision").format[Int]
  )(User.apply, unlift(User.unapply))

  case class UserClaim(value: User) extends ClaimValue {
    override val field: ClaimField = UserClaim
    override val jsValue: JsValue = Json.toJson(value)
  }
  object UserClaim extends ClaimField {
    override def attemptApply(value: JsValue): Option[ClaimValue] =
      value.asOpt[User].map(apply)

    override val name = "user"
  }
}

object JWT {
  import Claims.UserClaim

  private val secret = "Gordon Shumway" //TODO
  private val algorithm = Algorithm.HS256
  private val wustIss = Iss("wust")
  private val wustAud = Aud("wust")
  private def currentTimestamp: Long = System.currentTimeMillis / 1000

  private def expirationTimestamp = currentTimestamp + 86400 // 24h

  private def generateToken(user: User, expires: Long): DecodedJwt = new DecodedJwt(
    Seq(Alg(algorithm), Typ("JWT")),
    Seq(wustIss, wustAud, Exp(expires), UserClaim(user))
  )

  def generateAuthentication(user: User): Authentication = {
    val expires = expirationTimestamp
    val jwt = generateToken(user, expires)
    Authentication(user, expires, jwt.encodedAndSigned(secret))
  }

  def authenticationFromToken(token: Authentication.Token): Option[Authentication] = {
    DecodedJwt.validateEncodedJwt(
      token, secret, algorithm, Set(Typ),
      Set(Iss, Aud, Exp, UserClaim),
      iss = Some(wustIss), aud = Some(wustAud)
    ).toOption.flatMap { decoded =>
      for {
        expires <- decoded.getClaim[Exp]
        user <- decoded.getClaim[UserClaim]
      } yield {
        Authentication(user.value, expires.value, token)
      }
    }
  }

  def isExpired(auth: Authentication): Boolean = auth.expires <= currentTimestamp
}
