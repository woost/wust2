package wust.backend

import org.scalatest._
import org.mockito.Mockito._
import org.mockito.{ ArgumentMatchers => Args }
import wust.backend.auth.JWT
import wust.graph._
import wust.ids._
import wust.db.Data

import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._
import com.roundeights.hasher.Hasher

class AuthApiImplSpec extends AsyncFreeSpec with MustMatchers with ApiTestKit {
  implicit def passwordToDigest(pw: String): Array[Byte] = Hasher(pw).bcrypt
  // implicit class EqualityByteArray(val arr: Array[Byte]) {
  //   def mustEqualDigest(pw: String) = assert(passwordToDigest(pw)
  // }
  val jwt = new JWT("secret", 1 hours)

  "register" - {
    //TODO: username or password empty
    "no user" in mockDb { db =>
      db.group.memberships(Args.eq(UserId(0)))(Args.any()) returnsFuture Nil
      db.user.apply(Args.eq("torken"), Args.any())(Args.any()) returnsFuture Option(Data.User(0, "torken", false, 0))

      onAuthApi(State.initial, jwt, db = db)(_.register("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "override real user" in mockDb { db =>
      db.group.memberships(Args.eq(UserId(0)))(Args.any()) returnsFuture Nil
      db.user.apply(Args.eq("torken"), Args.any())(Args.any()) returnsFuture Option(Data.User(0, "torken", false, 0))

      val user = User(13, "dieter", false, 0)
      val auth = jwt.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), jwt, db = db)(_.register("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "activate implicit user" in mockDb { db =>
      db.group.memberships(Args.eq(UserId(13)))(Args.any()) returnsFuture Nil
      db.user.activateImplicitUser(Args.eq(UserId(13)), Args.eq("torken"), Args.any())(Args.any()) returnsFuture Option(Data.User(13, "torken", false, 0))

      val user = User(13, "anonieter", true, 0)
      val auth = jwt.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), jwt, db = db)(_.register("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "create fails and forgets real user" in mockDb { db =>
      db.user.apply(Args.eq("torken"), Args.any())(Args.any()) returnsFuture None

      val user = User(13, "dieter", false, 0)
      val auth = jwt.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), jwt, db = db)(_.register("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual false
      }
    }

    "create fails and remembers implicit user" in mockDb { db =>
      db.group.memberships(Args.eq(UserId(13)))(Args.any()) returnsFuture Nil
      db.user.activateImplicitUser(Args.eq(UserId(13)), Args.eq("torken"), Args.any())(Args.any()) returnsFuture None

      val user = User(13, "anonieter", true, 0)
      val auth = jwt.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), jwt, db = db)(_.register("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth mustEqual Some(auth)
          events.size mustEqual 0
          result mustEqual false
      }
    }
  }

  "login" - {
    "no user" in mockDb { db =>
      db.group.memberships(Args.eq(UserId(0)))(Args.any()) returnsFuture Nil
      db.user.getUserAndDigest(Args.eq("torken"))(Args.any()) returnsFuture Option((Data.User(0, "torken", false, 0), "sanh"))

      onAuthApi(State.initial, jwt, db = db)(_.login("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "override real user" in mockDb { db =>
      db.group.memberships(Args.eq(UserId(0)))(Args.any()) returnsFuture Nil
      db.user.getUserAndDigest(Args.eq("torken"))(Args.any()) returnsFuture Option((Data.User(0, "torken", false, 0), "sanh"))

      val user = User(13, "dieter", false, 0)
      val auth = jwt.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), jwt, db = db)(_.login("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "merge implicit user" in mockDb { db =>
      db.group.memberships(Args.eq(UserId(0)))(Args.any()) returnsFuture Nil
      db.user.getUserAndDigest(Args.eq("torken"))(Args.any()) returnsFuture Option((Data.User(0, "torken", false, 0), "sanh"))
      db.user.mergeImplicitUser(Args.eq(UserId(13)), Args.eq(UserId(0)))(Args.any()) returnsFuture true

      val user = User(13, "reiter der tafelrunde", true, 0)
      val auth = jwt.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), jwt, db = db)(_.login("torken", "sanh")).map {
        case (state, events, result) =>
          verify(db.user, times(1)).mergeImplicitUser(UserId(13), UserId(0))
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "get fails and forgets real user" in mockDb { db =>
      db.user.getUserAndDigest(Args.eq("torken"))(Args.any()) returnsFuture None

      val user = User(13, "dieter", false, 0)
      val auth = jwt.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), jwt, db = db)(_.login("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual false
      }
    }

    "get fails and remembers implicit user" in mockDb { db =>
      db.user.getUserAndDigest(Args.eq("torken"))(Args.any()) returnsFuture None

      val user = User(13, "anonieter", true, 0)
      val auth = jwt.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), jwt, db = db)(_.login("torken", "sanh")).map {
        case (state, events, result) =>
          state.auth mustEqual Some(auth)
          events.size mustEqual 0
          result mustEqual false
      }
    }
  }

  "loginToken" - {
    import DbConversions._
    val tokUser = User(0, "torken", true, 0)
    val tokAuth = jwt.generateAuthentication(tokUser)

    "invalid token" in mockDb { db =>
      onAuthApi(State.initial, jwt, db = db)(_.loginToken("invalid")).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual false
      }
    }

    "no user" in mockDb { db =>
      db.group.memberships(Args.eq(UserId(0)))(Args.any()) returnsFuture Nil
      db.user.checkIfEqualUserExists(Args.eq(tokUser))(Args.any()) returnsFuture true

      onAuthApi(State.initial, jwt, db = db)(_.loginToken(tokAuth.token)).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "override real user" in mockDb { db =>
      db.group.memberships(Args.eq(UserId(0)))(Args.any()) returnsFuture Nil
      db.user.checkIfEqualUserExists(Args.eq(tokUser))(Args.any()) returnsFuture true

      val user = User(13, "dieter", false, 0)
      val auth = jwt.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), jwt, db = db)(_.loginToken(tokAuth.token)).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "activate implicit user" in mockDb { db =>
      db.group.memberships(Args.eq(UserId(0)))(Args.any()) returnsFuture Nil
      db.user.checkIfEqualUserExists(Args.eq(tokUser))(Args.any()) returnsFuture true

      val user = User(13, "anonieter", true, 0)
      val auth = jwt.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), jwt, db = db)(_.loginToken(tokAuth.token)).map {
        case (state, events, result) =>
          state.auth.map(_.user.name) mustEqual Some("torken")
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "check fails and forgets real user" in mockDb { db =>
      db.user.checkIfEqualUserExists(Args.eq(tokUser))(Args.any()) returnsFuture false

      val user = User(13, "dieter", false, 0)
      val auth = jwt.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), jwt, db = db)(_.loginToken(tokAuth.token)).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual false
      }
    }

    "check fails and forgets implicit user" in mockDb { db =>
      db.user.checkIfEqualUserExists(Args.eq(tokUser))(Args.any()) returnsFuture false

      val user = User(13, "anonieter", true, 0)
      val auth = jwt.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), jwt, db = db)(_.loginToken(tokAuth.token)).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual false
      }
    }
  }

  "logout" - {
    "no user" in {
      onAuthApi(State.initial, jwt)(_.logout()).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual true
      }
    }

    "with user" in {
      val user = User(13, "anonieter", true, 0)
      val auth = jwt.generateAuthentication(user)

      onAuthApi(State.initial.copy(auth = Option(auth)), jwt)(_.logout()).map {
        case (state, events, result) =>
          state.auth mustEqual None
          events.size mustEqual 0
          result mustEqual true
      }
    }
  }
}
