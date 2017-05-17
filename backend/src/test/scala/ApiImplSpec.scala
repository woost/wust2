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
    def data(id: Long, name: String): wust.db.data.User = new wust.db.data.User(id, name, isImplicit = false, 0)
  }

  "addGroup" in mockDb { db =>
    db.group.createForUser(UserId(23)) returns Future.successful(Option((User.data(23, "dieter"), data.Membership(1, 23), data.UserGroup(1))))

    val auth = JWT.generateAuthentication(User(23, "hans"))
    val state = State.initial.copy(auth = Some(auth))
    onApi(state, db = db)(_.addGroup()).map {
      case (state, events, result) =>
        state.auth mustEqual Some(auth)
        events must contain theSameElementsAs Seq(NewMembership(User(23, "dieter"), Membership(23, 1), Group(1)))
        result mustEqual GroupId(1)
    }
  }

  "2x addGroup" in mockDb { db =>
    db.group.createForUser(UserId(23)) returns Future.successful(Option((User.data(23, "dieter"), data.Membership(1, 23), data.UserGroup(1))))

    val auth = JWT.generateAuthentication(User(23, "hans"))
    val state = State.initial.copy(auth = Some(auth))
    onApi(state, db = db)(_.addGroup()).flatMap {
      case (state1, events1, result1) =>
        onApi(state1, db = db)(_.addGroup()).map {
          case (state, events, result) =>

            state1.auth mustEqual Some(auth)
            events1 must contain theSameElementsAs Seq(NewMembership(User(23, "dieter"), Membership(23, 1), Group(1)))
            result1 mustEqual GroupId(1)

            state.auth mustEqual Some(auth)
            events must contain theSameElementsAs Seq(NewMembership(User(23, "dieter"), Membership(23, 1), Group(1)))
            result mustEqual GroupId(1)
        }
    }

  }
}
