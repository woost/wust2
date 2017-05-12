package wust.backend

import org.scalatest._
import wust.api._
import wust.ids._
import wust.graph._
import wust.backend.auth._

import scala.concurrent.Future

class StateTranslatorSpec extends FreeSpec with MustMatchers {
  import TestDefaults.jwt
  val user = User(14, "user", isImplicit = false, 0)
  val auth = jwt.generateAuthentication(user)

  "applyEvent" - {
    "NewMembership" - {
      "with new member as user" in {
        val group = GroupId(2)
        val state = State(Some(auth), groupIds = Set.empty)
        val membership = NewMembership(Membership(auth.user.id, group))

        val newState = StateTranslator.applyEvent(state, membership)

        newState.groupIds must contain theSameElementsAs Set(group)
      }

      "with different user" in {
        val group = GroupId(2)
        val state = State(Some(auth), groupIds = Set.empty)
        val membership = NewMembership(Membership(666, group))

        val newState = StateTranslator.applyEvent(state, membership)

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

        val allowed = StateTranslator.allowsEvent(state, membership)

        allowed mustEqual true
      }

      "with non-member" in {
        val group = GroupId(2)
        val state = State(Some(auth), groupIds = Set.empty)
        val membership = NewMembership(Membership(666, group))

        val allowed = StateTranslator.allowsEvent(state, membership)

        allowed mustEqual false
      }

      "with new user" in {
        val group = GroupId(2)
        val state = State(Some(auth), groupIds = Set.empty)
        val membership = NewMembership(Membership(auth.user.id, group))

        val allowed = StateTranslator.allowsEvent(state, membership)

        allowed mustEqual true
      }
    }
  }
}
