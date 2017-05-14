package wust.backend

import org.scalatest._
import wust.backend.auth.JWT
import wust.api._
import wust.ids._
import wust.db.{Db, data}
import wust.graph._
import scala.concurrent.Future

class StateChangeSpec extends AsyncFreeSpec with MustMatchers with DbMocks {
  val user = User(14, "user", isImplicit = false, 0)
  val auth = JWT.generateAuthentication(user)

  def newStateChange(db: Db = mockedDb, enableImplicit: Boolean = false) = new StateChange(db, enableImplicit)

  "filterValid" - {
    val stateChange = newStateChange()

    "valid" in {
      val state = State(auth = Some(auth), groupIds = Set.empty)
      val newState = stateChange.filterValid(state)
      newState mustEqual state
    }

    "invalid" in {
      val state = State(auth = Some(auth.copy(expires = 0)), groupIds = Set.empty)
      val newState = stateChange.filterValid(state)
      newState.auth mustEqual None
      newState.groupIds mustEqual state.groupIds
    }
  }

  "stateEvents" - {
    def emptyGraph = (Seq.empty[data.Post], Seq.empty[data.Connection], Seq.empty[data.Containment], Seq.empty[data.UserGroup], Seq.empty[data.Ownership], Seq.empty[data.User], Seq.empty[data.Membership])

    "with auth" in mockDb { db =>
      db.graph.getAllVisiblePosts(Some(user.id)) returns Future.successful(emptyGraph)
      val stateChange = newStateChange(db = db)

      val state = State(auth = Some(auth), groupIds = Set.empty)
      val events = Future.sequence(stateChange.stateEvents(state))

      events.map { events =>
        events must contain theSameElementsAs Seq(LoggedIn(auth.toAuthentication), ReplaceGraph(Graph.empty))
      }
    }

    "with groupIds" in mockDb { db =>
      db.graph.getAllVisiblePosts(None) returns Future.successful(emptyGraph)
      val stateChange = newStateChange(db = db)

      val state = State(auth = None, groupIds = Set(1,2))
      val events = Future.sequence(stateChange.stateEvents(state))

      events.map { events =>
        events must contain theSameElementsAs Seq(LoggedOut, ReplaceGraph(Graph.empty))
      }
    }

    "without auth" in mockDb { db =>
      val stateChange = newStateChange(db = db)
      db.graph.getAllVisiblePosts(None) returns Future.successful(emptyGraph)

      val state = State(auth = None, groupIds = Set.empty)
      val events = Future.sequence(stateChange.stateEvents(state))

      events.map { events =>
        events must contain theSameElementsAs Seq(LoggedOut, ReplaceGraph(Graph.empty))
      }
    }
  }

  "stateChangeEvents" - {
    def emptyGraph = (Seq.empty[data.Post], Seq.empty[data.Connection], Seq.empty[data.Containment], Seq.empty[data.UserGroup], Seq.empty[data.Ownership], Seq.empty[data.User], Seq.empty[data.Membership])

    "same state" in mockDb { db =>
      val stateChange = newStateChange(db = db)

      val state = State(auth = Some(auth), groupIds = Set.empty)
      val events = Future.sequence(stateChange.stateChangeEvents(state, state))

      events.map { events =>
        events.size mustEqual 0
      }
    }

    "different auth" in mockDb { db =>
      db.graph.getAllVisiblePosts(None) returns Future.successful(emptyGraph)
      val stateChange = newStateChange(db = db)

      val state = State(auth = Some(auth), groupIds = Set(1,2))
      val newState = state.copy(auth = None)
      val events = Future.sequence(stateChange.stateChangeEvents(state, newState))
      val expected = Future.sequence(stateChange.stateEvents(newState))

      for {
        events <- events
        expected <- expected
      } yield events must contain theSameElementsAs expected
    }
  }

  "createImplicitAuth" - {
    "disabled" in mockDb { db =>
      val stateChange = newStateChange(db = db, enableImplicit = false)

      val auth = stateChange.createImplicitAuth()

      auth.map(_ mustEqual None)
    }

    "enabled" in mockDb { db =>
      val implUser = dbT.User(13, "harals", true, 0)
      db.user.createImplicitUser() returns Future.successful(implUser)
      val stateChange = newStateChange(db = db, enableImplicit = true)

      val auth = stateChange.createImplicitAuth()

      auth.map { auth =>
        auth.map(_.user) mustEqual Some(DbConversions.forClient(implUser))
      }
    }
  }
}
