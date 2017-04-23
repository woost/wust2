package wust.backend.auth

import org.scalatest._
import wust.api.User

import scala.concurrent.Future

class AuthenticatedAccessSpec extends AsyncFreeSpec with MustMatchers {

  def validAuth(user: User) = JWTAuthentication(user, Long.MaxValue, "abc")
  def expiredAuth(user: User) = JWTAuthentication(user, 123L, "abc")
  class ErrorEx extends Exception("meh")

  def ApiAuth(auth: Option[JWTAuthentication], createImplicitAuth: () => Option[JWTAuthentication], toError: => Exception = new ErrorEx) =
    new AuthenticatedAccess(Future.successful(auth), () => Future.successful(createImplicitAuth()), toError)

  "no user, no implicit" - {
    val api = ApiAuth(None, () => None)

    "actualAuth" in api.actualAuth.map { auth =>
      auth must beNone
    }

    "createOrActualAuth" in api.createdOrActualAuth.map { auth =>
      auth must beNone
    }

    "actualOrImplicitAuth" in {
      api.actualOrImplicitAuth.map { auth =>
        auth must beNone
      }

      api.createdOrActualAuth.map { auth =>
        auth must beNone
      }
    }

    "withUserOpt" in api.withUserOpt { user =>
      user must beNone
    }

    "withUser" in recoverToSucceededIf[ErrorEx] {
      api.withUser(u => Future.successful(u))
    }

    "withUser 2" in recoverToSucceededIf[ErrorEx] {
      api.withUser(Future.successful(1))
    }

    "withUserOrImplicit" in recoverToSucceededIf[ErrorEx] {
      api.withUserOrImplicit(u => Future.successful(u))
    }

    "withUserOrImplicit 2" in recoverToSucceededIf[ErrorEx] {
      api.withUserOrImplicit(Future.successful(1))
    }
  }

  "no user, with implicit" - {
    val anonUser = User("anon")
    val anonAuth = validAuth(anonUser)
    val api = ApiAuth(None, () => Option(anonAuth))

    "actualAuth" in api.actualAuth.map { auth =>
      auth must beNone
    }

    "createOrActualAuth" in api.createdOrActualAuth.map { auth =>
      auth must beNone
    }

    "actualOrImplicitAuth" in {
      for {
        auth <- api.actualOrImplicitAuth
        created <- api.createdOrActualAuth
      } yield {
        auth mustEqual created
        auth mustEqual Option(anonAuth)
      }
    }

    "withUserOpt" in api.withUserOpt { user =>
      user must beNone
    }

    "withUser" in recoverToSucceededIf[ErrorEx] {
      api.withUser(u => Future.successful(u))
    }

    "withUser 2" in recoverToSucceededIf[ErrorEx] {
      api.withUser(Future.successful(1))
    }

    "withUserOrImplicit" in api.withUserOrImplicit { user =>
      user mustEqual anonUser
    }

    "withUserOrImplicit 2" in api.withUserOrImplicit {
      Future.successful(1 mustEqual 1)
    }
  }

  "no user, with expired implicit" - {
    val anonUser = User("anon")
    val anonAuth = expiredAuth(anonUser)
    val api = ApiAuth(None, () => Option(anonAuth))

    "actualAuth" in api.actualAuth.map { auth =>
      auth must beNone
    }

    "createOrActualAuth" in api.createdOrActualAuth.map { auth =>
      auth must beNone
    }

    "actualOrImplicitAuth" in {
      api.actualOrImplicitAuth.map { auth =>
        auth must beNone
      }

      api.createdOrActualAuth.map { auth =>
        auth must beNone
      }
    }

    "withUserOpt" in api.withUserOpt { user =>
      user must beNone
    }

    "withUser" in recoverToSucceededIf[ErrorEx] {
      api.withUser(u => Future.successful(u))
    }

    "withUser 2" in recoverToSucceededIf[ErrorEx] {
      api.withUser(Future.successful(1))
    }

    "withUserOrImplicit" in recoverToSucceededIf[ErrorEx] {
      api.withUserOrImplicit(u => Future.successful(u))
    }

    "withUserOrImplicit 2" in recoverToSucceededIf[ErrorEx] {
      api.withUserOrImplicit(Future.successful(1))
    }
  }

  "with user" - {
    val daUser = User("harals")
    val daAuth = validAuth(daUser)
    val api = ApiAuth(Option(daAuth), () => Option(daAuth))

    "actualAuth" in api.actualAuth.map { auth =>
      auth mustEqual Option(daAuth)
    }

    "createOrActualAuth" in api.createdOrActualAuth.map { auth =>
      auth mustEqual Option(daAuth)
    }

    "actualOrImplicitAuth" in {
      for {
        auth <- api.actualOrImplicitAuth
        created <- api.createdOrActualAuth
      } yield {
        auth mustEqual created
        auth mustEqual Option(daAuth)
      }
    }

    "withUserOpt" in api.withUserOpt { user =>
      user mustEqual Option(daUser)
    }

    "withUser" in api.withUser { user =>
      user mustEqual daUser
    }

    "withUser 2" in api.withUser {
      Future.successful(1 mustEqual 1)
    }

    "withUserOrImplicit" in api.withUserOrImplicit { user =>
      user mustEqual daUser
    }

    "withUserOrImplicit 2" in api.withUserOrImplicit {
      Future.successful(1 mustEqual 1)
    }
  }

  "with expired user" - {
    val daUser = User("harals")
    val daAuth = expiredAuth(daUser)
    val api = ApiAuth(Option(daAuth), () => Option(daAuth))

    "actualAuth" in api.actualAuth.map { auth =>
      auth must beNone
    }

    "createOrActualAuth" in api.createdOrActualAuth.map { auth =>
      auth must beNone
    }

    "actualOrImplicitAuth" in {
      api.actualOrImplicitAuth.map { auth =>
        auth must beNone
      }

      api.createdOrActualAuth.map { auth =>
        auth must beNone
      }
    }

    "withUserOpt" in api.withUserOpt { user =>
      user must beNone
    }

    "withUser" in recoverToSucceededIf[ErrorEx] {
      api.withUser(u => Future.successful(u))
    }

    "withUser 2" in recoverToSucceededIf[ErrorEx] {
      api.withUser(Future.successful(1))
    }

    "withUserOrImplicit" in recoverToSucceededIf[ErrorEx] {
      api.withUserOrImplicit(u => Future.successful(u))
    }

    "withUserOrImplicit 2" in recoverToSucceededIf[ErrorEx] {
      api.withUserOrImplicit(Future.successful(1))
    }
  }
}
