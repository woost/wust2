// package wust.backend

// import org.scalatest._
// import wust.backend.auth.JWT
// import wust.api._
// import wust.graph.{ Group, User }
// import wust.db.{ Db, Data }
// import wust.ids._
// import org.mockito.Mockito._
// import wust.graph.{ Graph, Group }

// import scala.concurrent.Future
// import scala.concurrent.duration._

// class GuardDslSpec extends AsyncFreeSpec with MustMatchers with DbMocks {
//   val implicitUserDb = Data.User(14, "implicit", isImplicit = true, 0)
//   val implicitUser = DbConversions.forClient(implicitUserDb)
//   val initialUser = User(11, "existing", isImplicit = false, 0)
//   val jwt = new JWT("secret", 1 hours)

//   override def mockDb[T](f: Db => T) = super.mockDb { db =>
//     db.user.createImplicitUser() returnsFuture implicitUserDb
//     f(db)
//   }

//   //TODO: use constructor without db and jwt
//   def implicitDsl(db: Db) = GuardDsl(jwt, db, true)
//   def nonImplicitDsl(db: Db) = GuardDsl(jwt, db, enableImplicit = false)

//   val authState = State(auth = Option(jwt.generateAuthentication(initialUser)), graph = Graph(groups = List(Group(1), Group(2))))
//   val nonAuthState = State(auth = None, graph = Graph.empty)

//   "withUser" - {
//     "has user and implicit" in mockDb { db =>
//       val dsl = implicitDsl(db)
//       import dsl._

//       val fun: State => Future[RequestResponse[String, ApiEvent]] = withUser { (state, user) =>
//         state.auth.map(_.user) mustEqual Option(user)
//         Future.successful(RequestResponse("str"))

//       }

//       val response = fun(authState)
//       verify(db.user, times(0)).createImplicitUser()
//       for {
//         response <- response
//       } yield {
//         response.result mustEqual "str"
//         response.events mustEqual Seq.empty
//       }
//     }

//     "has user and no implicit" in mockDb { db =>
//       val dsl = nonImplicitDsl(db)
//       import dsl._

//       val fun: State => Future[RequestResponse[String, ApiEvent]] = withUser { (state, user) =>
//         state.auth.map(_.user) mustEqual Option(user)
//         Future.successful(RequestResponse("str"))
//       }

//       val response = fun(authState)
//       verify(db.user, times(0)).createImplicitUser()
//       for {
//         response <- response
//       } yield {
//         response.result mustEqual "str"
//         response.events mustEqual Seq.empty
//       }
//     }

//     "has no user and implicit" in mockDb { db =>
//       val dsl = implicitDsl(db)
//       import dsl._

//       val fun: State => Future[RequestResponse[String, ApiEvent]] = withUser { (state, user) =>
//         state.auth.map(_.user) mustEqual Option(user)
//         Future.successful(RequestResponse("str"))
//       }

//       val r = an[ApiException] must be thrownBy fun(nonAuthState)
//       verify(db.user, times(0)).createImplicitUser()
//       r
//     }

//     "has no user and no implicit" in mockDb { db =>
//       val dsl = nonImplicitDsl(db)
//       import dsl._

//       val fun: State => Future[RequestResponse[String, ApiEvent]] = withUser { (state, user) =>
//         state.auth.map(_.user) mustEqual Option(user)
//         Future.successful(RequestResponse("str"))
//       }

//       val r = an[ApiException] must be thrownBy fun(nonAuthState)
//       verify(db.user, times(0)).createImplicitUser()
//       r
//     }
//   }

//   "withUserOrImplicit" - {
//     "has user and implicit" in mockDb { db =>
//       val dsl = implicitDsl(db)
//       import dsl._

//       val fun: State => StateEffect[State, String, ApiEvent] = withUserOrImplicit { (state, user, wasCreated) =>
//         state.auth.map(_.user) mustEqual Option(user)
//         Future.successful(RequestResponse("str"))
//       }

//       val StateEffect(state, response) = fun(authState)
//       verify(db.user, times(0)).createImplicitUser()
//       for {
//         state <- state
//         response <- response
//       } yield {
//         response.result mustEqual "str"
//         response.events mustEqual Seq.empty
//         state mustEqual authState
//       }
//     }

//     "has user and no implicit" in mockDb { db =>
//       val dsl = nonImplicitDsl(db)
//       import dsl._

//       val fun: State => StateEffect[State, String, ApiEvent] = withUserOrImplicit { (state, user, wasCreated) =>
//         state.auth.map(_.user) mustEqual Option(user)
//         Future.successful(RequestResponse("str"))
//       }

//       val StateEffect(state, response) = fun(authState)
//       verify(db.user, times(0)).createImplicitUser()
//       for {
//         state <- state
//         response <- response
//       } yield {
//         response.result mustEqual "str"
//         response.events mustEqual Seq.empty
//         state mustEqual authState
//       }
//     }

//     "has no user and implicit" in mockDb { db =>
//       val dsl = implicitDsl(db)
//       import dsl._

//       val fun: State => StateEffect[State, String, ApiEvent] = withUserOrImplicit { (state, user, wasCreated) =>
//         state.auth.map(_.user) mustEqual Option(user)
//         Future.successful(RequestResponse("str"))
//       }

//       val StateEffect(state, response) = fun(nonAuthState)
//       verify(db.user, times(1)).createImplicitUser()
//       for {
//         state <- state
//         response <- response
//       } yield {
//         response.result mustEqual "str"
//         response.events mustEqual Seq.empty
//         state.graph.groupIds mustEqual nonAuthState.graph.groupIds
//         state.auth.map(_.user) mustEqual Option(implicitUser)
//       }
//     }

//     "has no user and no implicit" in mockDb { db =>
//       val dsl = nonImplicitDsl(db)
//       import dsl._

//       val fun: State => StateEffect[State, String, ApiEvent] = withUserOrImplicit { (state, user, wasCreated) =>
//         state.auth.map(_.user) mustEqual Option(user)
//         Future.successful(RequestResponse("str"))
//       }

//       val StateEffect(state, response) = fun(nonAuthState)
//       verify(db.user, times(0)).createImplicitUser()
//       for {
//         state <- state
//       } yield {
//         state mustEqual nonAuthState
//       }

//       recoverToSucceededIf[ApiException] { response }
//     }

//     "has no user and multiple implicit are equal" in mockDb { db =>
//       val dsl = implicitDsl(db)
//       import dsl._

//       val fun: State => StateEffect[State, String, ApiEvent] = withUserOrImplicit { (state, user, wasCreated) =>
//         state.auth.map(_.user) mustEqual Option(user)
//         Future.successful(RequestResponse("str"))
//       }

//       val StateEffect(state, response) = fun(nonAuthState)
//       val StateEffect(state2, response2) = fun(nonAuthState)
//       verify(db.user, times(1)).createImplicitUser()
//       for {
//         state <- state
//         state2 <- state2
//         response <- response
//         response2 <- response2
//       } yield {
//         response.result mustEqual "str"
//         response.events mustEqual Seq.empty
//         response2.result mustEqual "str"
//         response2.events mustEqual Seq.empty
//         state.graph.groupIds mustEqual nonAuthState.graph.groupIds
//         state.auth.map(_.user) mustEqual Option(implicitUser)
//         state2.auth mustEqual state.auth
//       }
//     }
//   }
// }
