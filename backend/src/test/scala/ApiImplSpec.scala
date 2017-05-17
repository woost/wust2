package wust.backend

import org.scalatest._
import wust.backend.auth.JWT
import wust.graph._
import wust.ids._
import wust.db.data
import wust.api._

import scala.concurrent.Future

class ApiImplSpec extends AsyncFreeSpec with MustMatchers with ApiTestKit {

  object User {
    def apply(id: Long, name: String): User = new User(id, name, isImplicit = false, 0)
  }

  "addGroup" in mockDb { db =>
    db.group.createForUser(UserId(23)) returns Future.successful(Option((data.UserGroup(1), data.Membership(1, 23))))

    val auth = JWT.generateAuthentication(User(23, "hans"))
    val state = State.initial.copy(auth = Some(auth))
    onApi(state, db = db)(_.addGroup()).map { case (state, events, result) =>
      state.auth mustEqual Some(auth)
      events must contain theSameElementsAs Seq(NewGroup(Group(1)), NewMembership(Membership(23, 1)))
      result mustEqual Group(1)
    }
  }

  "2x addGroup" in mockDb { db =>
    db.group.createForUser(UserId(23)) returns Future.successful(Option((data.UserGroup(1), data.Membership(1, 23))))

    val auth = JWT.generateAuthentication(User(23, "hans"))
    val state = State.initial.copy(auth = Some(auth))
    onApi(state, db = db)(_.addGroup()).flatMap { case (state1, events1, result1) =>
      onApi(state1, db = db)(_.addGroup()).map { case (state, events, result) =>

        state1.auth mustEqual Some(auth)
        events1 must contain theSameElementsAs Seq(NewGroup(Group(1)), NewMembership(Membership(23, 1)))
        result1 mustEqual Group(1)

        state.auth mustEqual Some(auth)
        events must contain theSameElementsAs Seq(NewGroup(Group(1)), NewMembership(Membership(23, 1)))
        result mustEqual Group(1)
      }
    }

  }
}
