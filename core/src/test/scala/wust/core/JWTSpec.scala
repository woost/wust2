package wust.core

import java.time.Instant

import org.scalatest._
import wust.api.{AuthUser, Authentication}
import wust.core.auth.JWT
import wust.ids._

import scala.concurrent.duration._

class JWTSpec extends FreeSpec with MustMatchers {
  val jwt = new JWT("secret", 1 hours)

  def User(name: String): AuthUser.Persisted = new AuthUser.Real(UserId.fresh, name, 0)

  "generate valid auth for user" in {
    val user = User("Biermann")
    val auth = jwt.generateAuthentication(user)

    JWT.isExpired(auth) mustEqual false
    auth.user mustEqual user
    auth.expires must be > Instant.now.getEpochSecond
    auth.token.string.length must be > 0
  }

  "expired auth is expired" in {
    val user = User("Frau Mahlzahn")
    val jwt = new JWT("secret", tokenLifetime = Duration.Zero)
    val auth = jwt.generateAuthentication(user)

    JWT.isExpired(auth) mustEqual true
    auth.user mustEqual user
    auth.expires must be <= Instant.now.getEpochSecond
    auth.token.string.length must be > 0
  }

  "incompatible secret not valid" in {
    val user = User("Frau Mahlzahn")

    val jwt1 = new JWT("gisela", tokenLifetime = 1 hours)
    val auth = jwt1.generateAuthentication(user)

    val jwt2 = new JWT("hans", tokenLifetime = 1 hours)
    val noAuth = jwt2.authenticationFromToken(auth.token)

    noAuth mustEqual None
  }

  "authentication from token" in {
    val user = User("Pumuckl")
    val genAuth = jwt.generateAuthentication(user)
    val auth = jwt.authenticationFromToken(genAuth.token)

    auth mustEqual Option(genAuth)
  }

  "no authentication from invalid token" in {
    val auth = jwt.authenticationFromToken(Authentication.Token("invalid token"))

    auth mustEqual None
  }

  "no authentication from expired token" in {
    val user = User("Frau Mahlzahn")
    val jwt = new JWT("secret", tokenLifetime = Duration.Zero)
    val expiredToken = jwt.generateAuthentication(user).token
    val auth = jwt.authenticationFromToken(expiredToken)

    auth mustEqual None
  }
}
