package wust.backend

import org.scalatest._
import wust.backend.auth.JWT
import wust.api._
import wust.ids._
import wust.db.{Db, data}
import wust.graph._
import scala.concurrent.Future

class StateInterpreterSpec extends FreeSpec with MustMatchers {
  val user = User(14, "user", isImplicit = false, 0)
  val auth = JWT.generateAuthentication(user)

  "applyEvent" - {
    "NewMembership" - {
      "with new member as user" in {
        val group = GroupId(2)
        val state = State(Some(auth), groupIds = Set.empty)
        val membership = NewMembership(Membership(auth.user.id, group))

        val newState = StateInterpreter.applyEvent(state, membership)

        newState.groupIds must contain theSameElementsAs Set(group)
      }

      "with different user" in {
        val group = GroupId(2)
        val state = State(Some(auth), groupIds = Set.empty)
        val membership = NewMembership(Membership(666, group))

        val newState = StateInterpreter.applyEvent(state, membership)

        newState.groupIds.size mustEqual 0
      }
    }
  }

  "allowsEvent" - {
    "NewMembership" - {
      "with member" in {
        val group = GroupId(2)
        val state = State(None, groupIds = Set(group))
        val membership = NewMembership(Membership(666, group))

        val allowed = StateInterpreter.allowsEvent(state, membership)

        allowed mustEqual true
      }

      "with non-member" in {
        val group = GroupId(2)
        val state = State(Some(auth), groupIds = Set.empty)
        val membership = NewMembership(Membership(666, group))

        val allowed = StateInterpreter.allowsEvent(state, membership)

        allowed mustEqual false
      }

      "with new user" in {
        val group = GroupId(2)
        val state = State(Some(auth), groupIds = Set.empty)
        val membership = NewMembership(Membership(auth.user.id, group))

        val allowed = StateInterpreter.allowsEvent(state, membership)

        allowed mustEqual true
      }
    }
  }

  "filterValid" - {
    "valid" in {
      val state = State(auth = Some(auth), groupIds = Set.empty)
      val newState = StateInterpreter.filterValid(state)
      newState mustEqual state
    }

    "invalid" in {
      val state = State(auth = Some(auth.copy(expires = 0)), groupIds = Set.empty)
      val newState = StateInterpreter.filterValid(state)
      newState.auth mustEqual None
      newState.groupIds mustEqual state.groupIds
    }
  }
}

class StateInterpreterDbSpec extends AsyncFreeSpec with MustMatchers with DbMocks {
  val user = User(14, "user", isImplicit = false, 0)
  val auth = JWT.generateAuthentication(user)

  "stateEvents" - {
    def emptyGraph = (Seq.empty[data.Post], Seq.empty[data.Connection], Seq.empty[data.Containment], Seq.empty[data.UserGroup], Seq.empty[data.Ownership], Seq.empty[data.User], Seq.empty[data.Membership])

    "with auth" in mockDb { db =>
      db.graph.getAllVisiblePosts(Some(user.id)) returns Future.successful(emptyGraph)
      val stateChange = new StateInterpreter(db = db)

      val state = State(auth = Some(auth), groupIds = Set.empty)
      val events = Future.sequence(stateChange.stateEvents(state))

      events.map { events =>
        events must contain theSameElementsAs Seq(LoggedIn(auth.toAuthentication), ReplaceGraph(Graph.empty))
      }
    }

    "with groupIds" in mockDb { db =>
      db.graph.getAllVisiblePosts(None) returns Future.successful(emptyGraph)
      val stateChange = new StateInterpreter(db = db)

      val state = State(auth = None, groupIds = Set(1,2))
      val events = Future.sequence(stateChange.stateEvents(state))

      events.map { events =>
        events must contain theSameElementsAs Seq(LoggedOut, ReplaceGraph(Graph.empty))
      }
    }

    "without auth" in mockDb { db =>
      val stateChange = new StateInterpreter(db = db)
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
      val stateChange = new StateInterpreter(db = db)

      val state = State(auth = Some(auth), groupIds = Set.empty)
      val events = Future.sequence(stateChange.stateChangeEvents(state, state))

      events.map { events =>
        events.size mustEqual 0
      }
    }

    "different auth" in mockDb { db =>
      db.graph.getAllVisiblePosts(None) returns Future.successful(emptyGraph)
      val stateChange = new StateInterpreter(db = db)

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
}
