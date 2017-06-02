package wust.backend

import org.scalatest._
import org.mockito.{ArgumentMatchers => Args}
import wust.backend.auth.JWT
import wust.graph._
import wust.ids._
import wust.db.data

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import com.roundeights.hasher.Hasher

class AuthApiImplSpec extends AsyncFreeSpec with MustMatchers with ApiTestKit {
  implicit def passwordToDigest(pw: String): Array[Byte] = Hasher(pw).bcrypt
  // implicit class EqualityByteArray(val arr: Array[Byte]) {
  //   def mustEqualDigest(pw: String) = assert(passwordToDigest(pw)
  // }

  "register" - {
    //TODO: username or password empty
    "no user" in mockDb { db =>
      db.group.memberships(UserId(0)) returnsFuture Nil
      db.user.apply(Args.eq("torken"), Args.any()) returnsFuture Option(data.User(0, "torken", false, 0))

      onAuthApi(State.initial, db = db)(_.register("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "override real user" in mockDb { db =>
      db.group.memberships(UserId(0)) returnsFuture Nil
      db.user.apply(Args.eq("torken"), Args.any()) returnsFuture Option(data.User(0, "torken", false, 0))

      val user = User(13, "dieter", false, 0)
      val auth = JWT.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), db = db)(_.register("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "activate implicit user" in mockDb { db =>
      db.group.memberships(UserId(13)) returnsFuture Nil
      db.user.activateImplicitUser(Args.eq(UserId(13)), Args.eq("torken"), Args.any()) returnsFuture Option(data.User(13, "torken", false, 0))

      val user = User(13, "anonieter", true, 0)
      val auth = JWT.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), db = db)(_.register("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "create fails and forgets real user" in mockDb { db =>
      db.user.apply(Args.eq("torken"), Args.any()) returnsFuture None

      val user = User(13, "dieter", false, 0)
      val auth = JWT.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), db = db)(_.register("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual false
      }
    }

    "create fails and remembers implicit user" in mockDb { db =>
      db.group.memberships(UserId(13)) returnsFuture Nil
      db.user.activateImplicitUser(Args.eq(UserId(13)), Args.eq("torken"), Args.any()) returnsFuture None

      val user = User(13, "anonieter", true, 0)
      val auth = JWT.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), db = db)(_.register("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth mustEqual Some(auth)
          events.size mustEqual 0
          result mustEqual false
      }
    }
  }

  "login" - {
    "no user" in mockDb { db =>
      db.group.memberships(UserId(0)) returnsFuture Nil
      db.user.getUserAndDigest("torken") returnsFuture Option((data.User(0, "torken", false, 0), "sanh"))

      onAuthApi(State.initial, db = db)(_.login("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "override real user" in mockDb { db =>
      db.group.memberships(UserId(0)) returnsFuture Nil
      db.user.getUserAndDigest("torken") returnsFuture Option((data.User(0, "torken", false, 0), "sanh"))

      val user = User(13, "dieter", false, 0)
      val auth = JWT.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), db = db)(_.login("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "get fails and forgets real user" in mockDb { db =>
      db.user.getUserAndDigest("torken") returnsFuture None

      val user = User(13, "dieter", false, 0)
      val auth = JWT.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), db = db)(_.login("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual false
      }
    }

    "get fails and forgets implicit user" in mockDb { db =>
      db.user.getUserAndDigest("torken") returnsFuture None

      val user = User(13, "anonieter", true, 0)
      val auth = JWT.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), db = db)(_.login("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual false
      }
    }
  }

  "loginToken" - {
    import DbConversions._
    val tokUser = User(0, "torken", true, 0)
    val tokAuth = JWT.generateAuthentication(tokUser)

    "invalid token" in mockDb { db =>
      onAuthApi(State.initial, db = db)(_.loginToken("invalid")).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual false
      }
    }

    "no user" in mockDb { db =>
      db.group.memberships(UserId(0)) returnsFuture Nil
      db.user.checkIfEqualUserExists(tokUser) returnsFuture true

      onAuthApi(State.initial, db = db)(_.loginToken(tokAuth.token)).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "override real user" in mockDb { db =>
      db.group.memberships(UserId(0)) returnsFuture Nil
      db.user.checkIfEqualUserExists(tokUser) returnsFuture true

      val user = User(13, "dieter", false, 0)
      val auth = JWT.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), db = db)(_.loginToken(tokAuth.token)).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "activate implicit user" in mockDb { db =>
      db.group.memberships(UserId(0)) returnsFuture Nil
      db.user.checkIfEqualUserExists(tokUser) returnsFuture true

      val user = User(13, "anonieter", true, 0)
      val auth = JWT.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), db = db)(_.loginToken(tokAuth.token)).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "check fails and forgets real user" in mockDb { db =>
      db.user.checkIfEqualUserExists(tokUser) returnsFuture false

      val user = User(13, "dieter", false, 0)
      val auth = JWT.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), db = db)(_.loginToken(tokAuth.token)).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual false
      }
    }

    "check fails and forgets implicit user" in mockDb { db =>
      db.user.checkIfEqualUserExists(tokUser) returnsFuture false

      val user = User(13, "anonieter", true, 0)
      val auth = JWT.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), db = db)(_.loginToken(tokAuth.token)).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual false
      }
    }
  }

  "logout" - {
    "no user" in {
      onAuthApi(State.initial)(_.logout()).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "with user" in {
      val user = User(13, "anonieter", true, 0)
      val auth = JWT.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)))(_.logout()).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual true
      }
    }
  }
}
