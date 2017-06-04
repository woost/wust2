package wust.backend

import org.scalatest._
import wust.backend.auth.JWT
import wust.api._
import wust.ids._
import wust.db.{Db, data}
import DbConversions._
import wust.graph._
import scala.concurrent.Future

class StateInterpreterSpec extends AsyncFreeSpec with MustMatchers with DbMocks {
  object User {
    def apply(id: Long, name: String): User = new User(id, name, isImplicit = false, 0)
    def data(id: Long, name: String): wust.db.data.User = new wust.db.data.User(id, name, isImplicit = false, 0)
  }

  val user = User(14, "user")
  val auth = JWT.generateAuthentication(user)

  "validate" - {
    "valid" in mockDb { db =>
      val state = State(auth = Some(auth), graph = Graph.empty)
      val stateInterpreter = new StateInterpreter(db = db)
      val newState = stateInterpreter.validate(state)
      newState mustEqual state
    }

    "invalid" in mockDb { db =>
      val state = State(auth = Some(auth.copy(expires = 0)), graph = Graph.empty)
      val stateInterpreter = new StateInterpreter(db = db)
      val newState = stateInterpreter.validate(state)
      newState.auth mustEqual None
      newState.graph.groupIds mustEqual state.graph.groupIds
    }
  }

  "stateEvents" - {
    def emptyGraph = (Seq.empty[data.Post], Seq.empty[data.Connection], Seq.empty[data.Containment], Seq.empty[data.UserGroup], Seq.empty[data.Ownership], Seq.empty[data.User], Seq.empty[data.Membership])

    "with auth" in mockDb { db =>
      db.graph.getAllVisiblePosts(Some(user.id)) returnsFuture emptyGraph
      val stateInterpreter = new StateInterpreter(db = db)

      val state = State(auth = Some(auth), graph = Graph.empty)
      val events = Future.sequence(stateInterpreter.stateEvents(state))

      events.map { events =>
        events must contain theSameElementsAs Seq(LoggedIn(auth.toAuthentication), ReplaceGraph(Graph.empty))
      }
    }

    "with groupIds" in mockDb { db =>
      db.graph.getAllVisiblePosts(None) returnsFuture emptyGraph
      val stateInterpreter = new StateInterpreter(db = db)

      val state = State(auth = None, graph = Graph(groups = List(Group(1), Group(2))))
      val events = Future.sequence(stateInterpreter.stateEvents(state))

      events.map { events =>
        events must contain theSameElementsAs Seq(LoggedOut, ReplaceGraph(Graph.empty))
      }
    }

    "without auth" in mockDb { db =>
      val stateInterpreter = new StateInterpreter(db = db)
      db.graph.getAllVisiblePosts(None) returnsFuture emptyGraph

      val state = State(auth = None, graph = Graph.empty)
      val events = Future.sequence(stateInterpreter.stateEvents(state))

      events.map { events =>
        events must contain theSameElementsAs Seq(LoggedOut, ReplaceGraph(Graph.empty))
      }
    }
  }

  "stateChangeEvents" - {
    def emptyGraph = (Seq.empty[data.Post], Seq.empty[data.Connection], Seq.empty[data.Containment], Seq.empty[data.UserGroup], Seq.empty[data.Ownership], Seq.empty[data.User], Seq.empty[data.Membership])

    "same state" in mockDb { db =>
      val stateInterpreter = new StateInterpreter(db = db)

      val state = State(auth = Some(auth), graph = Graph.empty)
      val events = Future.sequence(stateInterpreter.stateChangeEvents(state, state))

      events.map { events =>
        events.size mustEqual 0
      }
    }

    "different auth" in mockDb { db =>
      db.graph.getAllVisiblePosts(None) returnsFuture emptyGraph
      val stateInterpreter = new StateInterpreter(db = db)

      val state = State(auth = Some(auth), graph = Graph(groups = List(Group(1), Group(2))))
      val newState = state.copy(auth = None)
      val events = Future.sequence(stateInterpreter.stateChangeEvents(state, newState))
      val expected = Future.sequence(stateInterpreter.stateEvents(newState))

      for {
        events <- events
        expected <- expected
      } yield events must contain theSameElementsAs expected
    }

      //TODO
    "triggeredEvents" - {
      // "NewMembership with non-member user" in mockDb { db =>
      //   val stateInterpreter = new StateInterpreter(db = db)

      //   val state = State.initial.copy(auth = None)
      //   val event = NewMembership(Membership(1337, 12))
      //   stateInterpreter.triggeredEvents(state, event).map { events =>
      //     events.size mustEqual 0
      //   }
      // }

      // "NewMembership with exising member user" in mockDb { db =>
      //   val aMember = User.data(777, "harals")

      //   db.user.get(aMember.id) returnsFuture Some(aMember)
      //   val stateInterpreter = new StateInterpreter(db = db)

      //   val group = Group(12)
      //   val graph = Graph(users = Seq(aMember), groups = Seq(group), memberships = Seq(Membership(user.id, group.id)))
      //   val state = State(auth = Some(auth), graph = graph)
      //   val event = NewMembership(Membership(aMember.id, group.id))
      //   stateInterpreter.triggeredEvents(state, event).map { events =>
      //     events must contain theSameElementsAs Seq(NewUser(aMember), event)
      //   }
      // }

      // "NewMembership with new member user" in mockDb { db =>
      //   val postInGroup = data.Post("eidie", "harhar")
      //   val aMember = User.data(777, "harals")
      //   val aGroup = data.UserGroup(12)
      //   val aMembership = data.Membership(aMember.id, aGroup.id)

      //   db.group.get(aGroup.id) returnsFuture Some(aGroup)
      //   db.group.members(aGroup.id) returnsFuture List((aMember, aMembership))
      //   db.group.getOwnedPosts(aGroup.id) returnsFuture List(postInGroup)
      //   val stateInterpreter = new StateInterpreter(db = db)

      //   val graph = Graph.empty
      //   val aAuth = JWT.generateAuthentication(aMember)
      //   val state = State(auth = Some(aAuth), graph = graph)
      //   val event = NewMembership(Membership(aMember.id, aGroup.id))
      //   stateInterpreter.triggeredEvents(state, event).map { events =>
      //     events must contain theSameElementsAs Seq(event, NewUser(aMember), NewGroup(aGroup), NewPost(postInGroup), NewOwnership(Ownership(postInGroup.id, aGroup.id)))
      //   }
      // }
    }
  }
}
