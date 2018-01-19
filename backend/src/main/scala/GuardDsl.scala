package wust.backend

import wust.api._
import wust.db.Db
import wust.backend.auth._
import wust.graph._
import DbConversions._

import scala.concurrent.{ ExecutionContext, Future }

//TODO instance for each client?
class GuardDsl(createImplicitAuth: () => Future[JWTAuthentication])(implicit ec: ExecutionContext) extends ApiDsl {
  private lazy val implicitAuth = createImplicitAuth()

  implicit class GuardedAction(actionFactory: Action.type) {
    def withUser[T](f: (State, User) => Future[ApiData.Action[T]]): ApiFunction[T] = Action { state =>
      state.auth match {
        case Some(auth) => f(state, auth.user)
        case None => Future.successful(Returns.error[T](ApiError.Unauthorized))
      }
    }

    def withUserOrImplicit[T](code: (State, User, Boolean) => Future[ApiData.Action[T]]): ApiFunction[T] = Effect { state =>
      state.auth match {
        case Some(auth) => Future.successful(Returns(state, code(state, auth.user, false)))
        case None => for {
          auth <- implicitAuth
          newState = state.copy(auth = Some(auth))
        } yield Returns(newState, code(newState, auth.user, true))
      }
    }
  }
}

object GuardDsl {
  def apply(jwt: JWT, db: Db)(implicit ec: ExecutionContext): GuardDsl =
    new GuardDsl(() => db.user.createImplicitUser().map(user => jwt.generateAuthentication(user)))
}
