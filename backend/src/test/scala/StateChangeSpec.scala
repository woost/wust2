package wust.backend

import org.scalatest._
import wust.db.Db
import wust.graph._

class StateChangeSpec extends FreeSpec with MustMatchers with DbMocks {
  import TestDefaults.jwt
  val user = User(14, "user", isImplicit = false, 0)
  val auth = jwt.generateAuthentication(user)

  def newStateChange(db: Db = mockedDb, enableImplicit: Boolean = false) = new StateChange(mockedDb, jwt, enableImplicit)

  "filterValid" - {
    val stateChange = newStateChange()

    "valid" - {
      val state = State(auth = Some(auth), groupIds = Set.empty)
      val newState = stateChange.filterValid(state)
      newState mustEqual state
    }

    "invalid" - {
      val state = State(auth = Some(auth.copy(expires = 0)), groupIds = Set.empty)
      val newState = stateChange.filterValid(state)
      newState.auth mustEqual None
      newState.groupIds mustEqual state.groupIds
    }
  }
}
