package wust.backend

import org.scalatest._
import wust.backend.auth.JWT
import wust.api._
import wust.ids._
import wust.db.{ Db, data }
import wust.graph._
import scala.concurrent.Future

class StateInterpreterSpec extends FreeSpec with MustMatchers {
  val user = User(14, "user", isImplicit = false, 0)
  val auth = JWT.generateAuthentication(user)

  "validate" - {
    "valid" in {
      val state = State(auth = Some(auth), graph = Graph.empty)
      val newState = StateInterpreter.validate(state)
      newState mustEqual state
    }

    "invalid" in {
      val state = State(auth = Some(auth.copy(expires = 0)), graph = Graph.empty)
      val newState = StateInterpreter.validate(state)
      newState.auth mustEqual None
      newState.graph.groupIds mustEqual state.graph.groupIds
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

      val state = State(auth = Some(auth), graph = Graph.empty)
      val events = Future.sequence(stateChange.stateEvents(state))

      events.map { events =>
        events must contain theSameElementsAs Seq(LoggedIn(auth.toAuthentication), ReplaceGraph(Graph.empty))
      }
    }

    "with groupIds" in mockDb { db =>
      db.graph.getAllVisiblePosts(None) returns Future.successful(emptyGraph)
      val stateChange = new StateInterpreter(db = db)

      val state = State(auth = None, graph = Graph(groups = List(Group(1), Group(2))))
      val events = Future.sequence(stateChange.stateEvents(state))

      events.map { events =>
        events must contain theSameElementsAs Seq(LoggedOut, ReplaceGraph(Graph.empty))
      }
    }

    "without auth" in mockDb { db =>
      val stateChange = new StateInterpreter(db = db)
      db.graph.getAllVisiblePosts(None) returns Future.successful(emptyGraph)

      val state = State(auth = None, graph = Graph.empty)
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

      val state = State(auth = Some(auth), graph = Graph.empty)
      val events = Future.sequence(stateChange.stateChangeEvents(state, state))

      events.map { events =>
        events.size mustEqual 0
      }
    }

    "different auth" in mockDb { db =>
      db.graph.getAllVisiblePosts(None) returns Future.successful(emptyGraph)
      val stateChange = new StateInterpreter(db = db)

      val state = State(auth = Some(auth), graph = Graph(groups = List(Group(1), Group(2))))
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
