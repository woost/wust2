package wust.backend

import wust.ids._
import wust.api._
import wust.db.Db
import wust.backend.auth._
import wust.graph._
import DbConversions._

import scala.concurrent.{ ExecutionContext, Future }

class GuardDsl(createImplicitAuth: (UserId, String) => Future[Option[Authentication.Verified]])(implicit ec: ExecutionContext) extends ApiDsl {

  abstract class GuardedOps[F[+_]](factory: ApiFunctionFactory[F], errorFactory: ApiError.HandlerFailure => F[Nothing]) {
    def withUser[T](f: (State, User) => Future[F[T]]): ApiFunction[T] = factory { state =>
      state.auth match {
        case auth: Authentication.Verified => f(state, auth.user)
        case _ => Future.successful(errorFactory(ApiError.Unauthorized))
      }
    }
    //TODO require realuser

    def withUserOrImplicit[T](f: (State, User) => Future[F[T]]): ApiFunction[T] = withUser(f).redirectWithEvents { state =>
      state.auth match {
        case auth: Authentication.Assumed => createImplicitAuth(auth.user.id, auth.user.name).map(_.map(ApiEvent.LoggedIn(_)).toSeq)
        case _ => Future.successful(Seq.empty)
      }
    }
  }

  implicit class GuardedAction(factory: Action.type) extends GuardedOps[ApiData.Action](factory, Returns.error)
  implicit class GuardedEffect(factory: Effect.type) extends GuardedOps[ApiData.Effect](factory, Returns.error)
}

object GuardDsl {
  def apply(jwt: JWT, db: Db)(implicit ec: ExecutionContext): GuardDsl =
    new GuardDsl({ (userId, userName) =>
      db.user
        .createImplicitUser(userId, userName)
        .map(_.map(user => jwt.generateAuthentication(user).toAuthentication))
    })
}
