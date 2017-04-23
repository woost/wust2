package wust.backend.auth

import org.scalatest._
import wust.api.User

class JWTSpec extends FreeSpec with MustMatchers {
  "generate auth for user" in {
    val user = User("Biermann")
    val auth = JWT.generateAuthentication(user)

    auth.user mustEqual user
    auth.expires must be > (System.currentTimeMillis / 1000)
    auth.token.length must be > 0
  }

  "generated auth is not expired" in {
    val user = User("Frau Mahlzahn")
    val auth = JWT.generateAuthentication(user)

    JWT.isExpired(auth) mustEqual false
  }

  "expired auth is expired" in {
    val user = User("Frau Mahlzahn")
    val auth = JWT.generateAuthentication(user).copy(expires = 0)
    JWT.isExpired(auth) mustEqual true
  }

  "authentication from token" in {
    val user = User("Pumuckl")
    val genAuth = JWT.generateAuthentication(user)
    val auth = JWT.authenticationFromToken(genAuth.token)

    auth mustEqual Option(genAuth)
  }

  "no authentication from invalid token" in {
    val auth = JWT.authenticationFromToken("invalid token")

    auth mustEqual None
  }
}
