package wust.backend.auth

import org.scalatest._
import wust.graph.User
import wust.ids._

class jwtSpec extends FreeSpec with MustMatchers {
  val jwt: JWT = new JWT(secret = "secret", tokenLifetime = 12345678)

  implicit def intToUserId(id: Int): UserId = UserId(id)

  object User {
    def apply(name: String): User = new User(0, name, isImplicit = false, wust.db.User.initialRevision)
  }

  "generate auth for user" in {
    val user = User("Biermann")
    val auth = jwt.generateAuthentication(user)

    auth.user mustEqual user
    auth.expires must be > (System.currentTimeMillis / 1000)
    auth.token.length must be > 0
  }

  "generated auth is not expired" in {
    val user = User("Frau Mahlzahn")
    val auth = jwt.generateAuthentication(user)

    jwt.isExpired(auth) mustEqual false
  }

  "expired auth is expired" in {
    val user = User("Frau Mahlzahn")
    val jwt = new JWT("secret", tokenLifetime = 0)
    val auth = jwt.generateAuthentication(user)
    jwt.isExpired(auth) mustEqual true
  }

  "incompatible secret not valid" in {
    val user = User("Frau Mahlzahn")

    val jwt1 = new JWT("gisela", tokenLifetime = 12345678)
    val auth = jwt1.generateAuthentication(user)

    val jwt2 = new JWT("hans", tokenLifetime = 12345678)
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
}
