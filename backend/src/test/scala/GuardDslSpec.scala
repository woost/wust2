package wust.backend

import org.scalatest._
import wust.backend.auth.JWT
import wust.api._
import wust.graph.{Group, User}
import wust.db.{Db, data}
import wust.ids._
import org.mockito.Mockito._
import wust.framework.state._
import wust.graph.{Graph, Group}

import scala.concurrent.Future

class GuardDslSpec extends AsyncFreeSpec with MustMatchers with DbMocks {
  val implicitUserDb = data.User(14, "implicit", isImplicit = true, 0)
  val implicitUser = DbConversions.forClient(implicitUserDb)
  val initialUser = User(11, "existing", isImplicit = false, 0)

  override def mockDb[T](f: Db => T) = super.mockDb { db =>
    db.user.createImplicitUser() returns Future.successful(implicitUserDb)
    f(db)
  }

  def implicitDsl(db: Db) = new GuardDsl(db, true)
  def nonImplicitDsl(db: Db) = new GuardDsl(db, enableImplicit = false)

  val authState = State(auth = Option(JWT.generateAuthentication(initialUser)), graph = Graph(groups = List(Group(1), Group(2))))
  val nonAuthState = State(auth = None, graph = Graph.empty)

  "withUser" - {
    "has user and implicit" in mockDb { db =>
      val dsl = implicitDsl(db)
      import dsl._

      val fun: State => StateEffect[State, String, ApiEvent] = withUser { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))

      }

      verify(db.user, times(0)).createImplicitUser()
      val StateEffect(None, response) = fun(authState)
      for {
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
      }
    }

    "has user and no implicit" in mockDb { db =>
      val dsl = nonImplicitDsl(db)
      import dsl._

      val fun: State => StateEffect[State, String, ApiEvent] = withUser { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      verify(db.user, times(0)).createImplicitUser()
      val StateEffect(None, response) = fun(authState)
      for {
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
      }
    }

    "has no user and implicit" in mockDb { db =>
      val dsl = implicitDsl(db)
      import dsl._

      val fun: State => StateEffect[State, String, ApiEvent] = withUser { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      verify(db.user, times(0)).createImplicitUser()
      an[ApiException] must be thrownBy fun(nonAuthState)
    }

    "has no user and no implicit" in mockDb { db =>
      val dsl = nonImplicitDsl(db)
      import dsl._

      val fun: State => StateEffect[State, String, ApiEvent] = withUser { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      verify(db.user, times(0)).createImplicitUser()
      an[ApiException] must be thrownBy fun(nonAuthState)
    }
  }

  "withUserOrImplicit" - {
    "has user and implicit" in mockDb { db =>
      val dsl = implicitDsl(db)
      import dsl._

      val fun: State => StateEffect[State, String, ApiEvent] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      verify(db.user, times(0)).createImplicitUser()
      val StateEffect(Some(state), response) = fun(authState)
      for {
        state <- state
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
        state mustEqual authState
      }
    }

    "has user and no implicit" in mockDb { db =>
      val dsl = nonImplicitDsl(db)
      import dsl._

      val fun: State => StateEffect[State, String, ApiEvent] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      verify(db.user, times(0)).createImplicitUser()
      val StateEffect(Some(state), response) = fun(authState)
      for {
        state <- state
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
        state mustEqual authState
      }
    }

    "has no user and implicit" in mockDb { db =>
      val dsl = implicitDsl(db)
      import dsl._

      val fun: State => StateEffect[State, String, ApiEvent] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      // verify(db.user, times(1)).createImplicitUser() //TODO
      val StateEffect(Some(state), response) = fun(nonAuthState)
      for {
        state <- state
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
        state.graph.groupIds mustEqual nonAuthState.graph.groupIds
        state.auth.map(_.user) mustEqual Option(implicitUser)
      }
    }

    "has no user and no implicit" in mockDb { db =>
      val dsl = nonImplicitDsl(db)
      import dsl._

      val fun: State => StateEffect[State, String, ApiEvent] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      verify(db.user, times(0)).createImplicitUser()
      val StateEffect(Some(state), response) = fun(nonAuthState)
      for {
        state <- state
      } yield {
        state mustEqual nonAuthState
      }

      recoverToSucceededIf[ApiException] { response }
    }

    "has no user and multiple implicit are equal" in mockDb { db =>
      val dsl = implicitDsl(db)
      import dsl._

      val fun: State => StateEffect[State, String, ApiEvent] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      // verify(db.user, times(1)).createImplicitUser() //TODO
      val StateEffect(Some(state), response) = fun(nonAuthState)
      val StateEffect(Some(state2), response2) = fun(nonAuthState)
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
        state.graph.groupIds mustEqual nonAuthState.graph.groupIds
        state.auth.map(_.user) mustEqual Option(implicitUser)
        state2.auth mustEqual state.auth
      }
    }
  }
}
