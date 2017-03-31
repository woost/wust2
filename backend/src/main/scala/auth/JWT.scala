package wust.backend.auth

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import io.igl.jwt._
import play.api.libs.json.{JsNumber, JsArray, JsString, JsValue}

import wust.api._

case class UserId(value: Long) extends ClaimValue {
  override val field: ClaimField = UserId
  override val jsValue: JsValue = JsNumber(value)
}
object UserId extends ClaimField {
  override def attemptApply(value: JsValue): Option[ClaimValue] =
    value.asOpt[Long].map(apply)

  override val name = "userid"
}

object JWT {
  private val secret = "Gordon Shumway" //TODO
  private val algorithm = Algorithm.HS256
  private val wustIss = Iss("wust")
  private val wustAud = Aud("wust")
  private def currentTimestamp: Long = System.currentTimeMillis / 1000
  private def expirationTimestamp = currentTimestamp + 86400 // 24h

  private def generateToken(user: User, expires: Long): DecodedJwt = new DecodedJwt(
    Seq(Alg(algorithm), Typ("JWT")),
    Seq(wustIss, wustAud, Exp(expires), Sub(user.name), UserId(user.id))
  )

  def generateAuthentication(user: User): Authentication = {
    val expires = expirationTimestamp
    val jwt = generateToken(user, expires)
    Authentication(user, expires, jwt.encodedAndSigned(secret))
  }

  def authenticationFromToken(token: Authentication.Token): Option[Authentication] = {
    DecodedJwt.validateEncodedJwt(
      token, secret, algorithm, Set(Typ),
      Set(Iss, Aud, Exp, Sub, UserId),
      iss = Some(wustIss), aud = Some(wustAud)
    ).toOption.flatMap { decoded =>
      for {
        expires <- decoded.getClaim[Exp]
        userName <- decoded.getClaim[Sub]
        userId <- decoded.getClaim[UserId]
      } yield {
        val user = User(userId.value, userName.value)
        Authentication(user, expires.value, token)
      }
    }
  }

  def isExpired(auth: Authentication): Boolean = auth.expires <= currentTimestamp
}
