package wust.backend

import org.scalatest._
import wust.api._
import wust.ids._
import wust.graph.User
import wust.backend.auth._

import scala.concurrent.Future

class StateDslSpec extends AsyncFreeSpec with MustMatchers {
  val jwt = new JWT("secret", 12345678)

  val implicitUser = new User(14, "implicit", isImplicit = true, 0)
  val initialUser = new User(11, "existing", isImplicit = false, 0)

  def implicitDsl = {
    var count = -1
    new StateDsl(() => {
      count = count + 1
      Future.successful(Option(implicitUser.copy(revision = count)))
    })
  }
  def nonImplicitDsl = new StateDsl(() => Future.successful(None))

  val authState = State(auth = Option(jwt.generateAuthentication(initialUser)), groupIds = Set(1,2))
  val nonAuthState = State(auth = None, groupIds = Set.empty)

  "withUser" - {
    "has user and implicit" in {
      val dsl = implicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUser { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val NoEffect(response) = fun(authState)
      for {
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
      }
    }

    "has user and no implicit" in {
      val dsl = nonImplicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUser { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val NoEffect(response) = fun(authState)
      for {
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
      }
    }

    "has no user and implicit" in {
      val dsl = implicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUser { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      an [ApiException] must be thrownBy fun(nonAuthState)
    }

    "has no user and no implicit" in {
      val dsl = nonImplicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUser { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      an [ApiException] must be thrownBy fun(nonAuthState)
    }
  }

  "withUserOrImplicit" - {
    "has user and implicit" in {
      val dsl = implicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val StateEffect(state, response) = fun(authState)
      for {
        state <- state
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
        state mustEqual authState
      }
    }

    "has user and no implicit" in {
      val dsl = nonImplicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val StateEffect(state, response) = fun(authState)
      for {
        state <- state
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
        state mustEqual authState
      }
    }

    "has no user and implicit" in {
      val dsl = implicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val StateEffect(state, response) = fun(nonAuthState)
      for {
        state <- state
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
        state.groupIds mustEqual nonAuthState.groupIds
        state.auth.map(_.user) mustEqual Option(implicitUser)
      }
    }

    "has no user and no implicit" in {
      val dsl = nonImplicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val StateEffect(state, response) = fun(nonAuthState)
      for {
        state <- state
      } yield {
        state mustEqual nonAuthState
      }

      recoverToSucceededIf[ApiException] { response }
    }

    "has no user and multiple implicit are equal" in {
      val dsl = implicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val StateEffect(state, response) = fun(nonAuthState)
      val StateEffect(state2, response2) = fun(nonAuthState)
      for {
        state <- state
        state2 <- state2
        response <- response
        response2 <- response2
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
        response2.result mustEqual "str"
        response2.events mustEqual Seq.empty
        state.groupIds mustEqual nonAuthState.groupIds
        state.auth.map(_.user) mustEqual Option(implicitUser)
        state2.auth.map(_.user) mustEqual Option(implicitUser)
      }
    }
  }
}

class StateAccessSpec extends AsyncFreeSpec with MustMatchers {
}
