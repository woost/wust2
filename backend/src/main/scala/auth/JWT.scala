package wust.backend.auth

import wust.graph.User
import io.igl.jwt._
import wust.ids._
import wust.api._
import wust.backend.config.Config

object Claims {
  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  implicit val userIdReader = Reads.of[Long].map(new UserId(_))
  implicit val userIdWriter = Writes { (user: UserId) => JsNumber(user.id) }
  implicit val userFormat = (
    (__ \ "id").format[UserId] ~
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

//@derive((user, expires) => toString)
case class JWTAuthentication private[auth] (user: User, expires: Long, token: Authentication.Token) {
  def toAuthentication = Authentication(user, token)
}

class JWT(secret: String, tokenLifetime: Long) {
  import Claims.UserClaim

  private val algorithm = Algorithm.HS256
  private val wustIss = Iss("wust")
  private val wustAud = Aud("wust")
  private def currentTimestamp: Long = System.currentTimeMillis / 1000
  private def expirationTimestamp = currentTimestamp + tokenLifetime

  def generateToken(user: User, expires: Long): DecodedJwt = new DecodedJwt(
    Seq(Alg(algorithm), Typ("JWT")),
    Seq(wustIss, wustAud, Exp(expires), UserClaim(user))
  )

  def generateAuthentication(user: User): JWTAuthentication = {
    val expires = expirationTimestamp
    val jwt = generateToken(user, expires)
    JWTAuthentication(user, expires, jwt.encodedAndSigned(secret))
  }

  def authenticationFromToken(token: Authentication.Token): Option[JWTAuthentication] = {
    DecodedJwt.validateEncodedJwt(
      token, secret, algorithm, Set(Typ),
      Set(Iss, Aud, Exp, UserClaim),
      iss = Option(wustIss), aud = Option(wustAud)
    ).toOption.flatMap { decoded =>
        for {
          expires <- decoded.getClaim[Exp]
          user <- decoded.getClaim[UserClaim]
        } yield {
          JWTAuthentication(user.value, expires.value, token)
        }
      }.filterNot(isExpired)
  }

  def isExpired(auth: JWTAuthentication): Boolean = auth.expires <= currentTimestamp
}
object JWT {
  val default = new JWT(Config.auth.secret, Config.auth.tokenLifetime)
}
