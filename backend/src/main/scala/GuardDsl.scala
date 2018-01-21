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
    private def requireUserT[T, U <: User](f: (State, U) => Future[F[T]])(userf: PartialFunction[User, U]): ApiFunction[T] = factory { state =>
      state.auth.dbUserOpt
        .collect(userf andThen (f(state, _)))
        .getOrElse(Future.successful(errorFactory(ApiError.Unauthorized)))
    }

    def requireImplicitUser[T](f: (State, User.Implicit) => Future[F[T]]): ApiFunction[T] = requireUserT[T, User.Implicit](f) { case u: User.Implicit => u }
    def requireAssumedUser[T](f: (State, User.Assumed) => Future[F[T]]): ApiFunction[T] = requireUserT[T, User.Assumed](f) { case u: User.Assumed => u }
    def requireRealUser[T](f: (State, User.Real) => Future[F[T]]): ApiFunction[T] = requireUserT[T, User.Real](f) { case u: User.Real => u }
    def requireAnyUser[T](f: (State, User) => Future[F[T]]): ApiFunction[T] = requireUserT[T, User](f)(PartialFunction(identity))
    def requireDbUser[T](f: (State, User.Persisted) => Future[F[T]]): ApiFunction[T] = requireUserT[T, User.Persisted](f) { case u: User.Persisted => u }

    def assureDbUser[T](f: (State, User.Persisted) => Future[F[T]]): ApiFunction[T] = requireDbUser(f).redirectWithEvents { state =>
      state.auth.userOpt match {
        case Some(user: User.Assumed) => createImplicitAuth(user.id, user.name).map(_.map(ApiEvent.LoggedIn(_)).toSeq)
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
