// package wust.core

// import org.scalatest._
// import wust.api._
// import wust.core.auth.JWT
// import wust.db.Data
// import wust.graph._
// import wust.ids._

// import java.time.LocalDateTime
// import scala.concurrent.duration._

// class ApiImplSpec extends AsyncFreeSpec with must.Matchers with ApiTestKit {

//   object User {
//     def apply(id: Long, name: String): User = new User(id, name, isImplicit = false, 0)
//     def data(id: Long, name: String): Data.User = new Data.User(id, name, isImplicit = false, 0)
//   }

//   val jwt = new JWT("secret", 1 hours)

//   "getPost" in mockDb { db =>
//     val nodeId = "goink"
//     db.post.get(nodeId) returnsFuture Option(Data.Post(nodeId, "banga", 0, LocalDateTime.of(2018,11,11,11,11), LocalDateTime.of(2018,11,11,11,11)))
//     onApi(State.initial, jwt, db)(_.getPost(nodeId)).map {
//       case (state, events, result) =>
//         state mustEqual State.initial
//         events must contain theSameElementsAs List()
//         result mustEqual Option(Post(nodeId, "banga", 0, LocalDateTime.of(2018,11,11,11,11), LocalDateTime.of(2018,11,11,11,11)))
//     }
//   }

//   "addGroup" in mockDb { db =>
//     db.group.createForUser(UserId(23)) returnsFuture Option((User.data(23, "dieter"), Data.Membership(23, 1), Data.UserGroup(1)))

//     val auth = jwt.generateAuthentication(User(23, "hans"))
//     val state = State.initial.copy(auth = Some(auth))
//     onApi(state, jwt, db)(_.addGroup()).map {
//       case (state, events, result) =>
//         state.auth mustEqual Some(auth)
//         events must contain theSameElementsAs Seq(NewMembership(Membership(23, 1)))
//         result mustEqual GroupId(1)
//     }
//   }

//   // historic test
//   "2x addGroup" in mockDb { db =>
//     db.group.createForUser(UserId(23)) returnsFuture Option((User.data(23, "dieter"), Data.Membership(23, 1), Data.UserGroup(1)))

//     val auth = jwt.generateAuthentication(User(23, "hans"))
//     val state = State.initial.copy(auth = Some(auth))
//     onApi(state, jwt, db)(_.addGroup()).flatMap {
//       case (state1, events1 @ _, result1) =>
//         onApi(state1, jwt, db = db)(_.addGroup()).map {
//           case (state, events, result) =>
//             state1.auth mustEqual Some(auth)
//             events must contain theSameElementsAs Seq(NewMembership(Membership(23, 1)))
//             result1 mustEqual GroupId(1)

//             state.auth mustEqual Some(auth)
//             events must contain theSameElementsAs Seq(NewMembership(Membership(23, 1)))
//             result mustEqual GroupId(1)
//         }
//     }
//   }
// }
