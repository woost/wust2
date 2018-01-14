package wust.backend.auth

import org.scalatest._
import wust.graph.User
import wust.ids._

import scala.concurrent.duration._

class JWTSpec extends FreeSpec with MustMatchers {
  val jwt = new JWT("secret", 1 hours)

  implicit def intToUserId(id: Int): UserId = UserId(id)

  object User {
    def apply(name: String): User = new User(1313L, name, isImplicit = false, 0)
  }

  "generate auth and then to authentication" in {
    val user = User("Biermann")
    val auth = jwt.generateAuthentication(user)
    val clientAuth = auth.toAuthentication

    clientAuth.user mustEqual auth.user
    clientAuth.token mustEqual auth.token
  }

  "generate valid auth for user" in {
    val user = User("Biermann")
    val auth = jwt.generateAuthentication(user)

    auth.isExpired mustEqual false
    auth.user mustEqual user
    auth.expires must be > (System.currentTimeMillis / 1000)
    auth.token.length must be > 0
  }

  "expired auth is expired" in {
    val user = User("Frau Mahlzahn")
    val jwt = new JWT("secret", tokenLifetime = Duration.Zero)
    val auth = jwt.generateAuthentication(user)

    auth.isExpired mustEqual true
    auth.user mustEqual user
    auth.expires must be < (System.currentTimeMillis / 1000)
    auth.token.length must be > 0
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
    val auth = jwt.authenticationFromToken("invalid token")

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
