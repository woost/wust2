package wust.backend

import org.scalatest._

import wust.api.User
import auth.JWT

class JWTSpec extends FreeSpec with MustMatchers {
  "generate token for user" in {
    val (expires, token) = JWT.generateToken(User(12, "Biermann"))

    expires must be > (System.currentTimeMillis / 1000)
    token.length must be > 0
  }

  "no authentication from invalid token" in {
    val auth = JWT.authenticationFromToken("invalid token")

    auth mustEqual None
  }

  "authentication from token" in {
    val user = User(1, "Pumuckl")
    val (expires, token) = JWT.generateToken(user)
    val auth = JWT.authenticationFromToken(token)

    auth.isDefined mustEqual true
    auth.get.token mustEqual token
    auth.get.expires mustEqual expires
    auth.get.user mustEqual user
  }

  "authentication is not expired" in {
    val user = User(0, "Frau Mahlzahn")
    val (_, token) = JWT.generateToken(user)
    val auth = JWT.authenticationFromToken(token)

    auth.isDefined mustEqual true
    JWT.isExpired(auth.get) mustEqual false
  }
}
