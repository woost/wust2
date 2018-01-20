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

  abstract class GuardedOps[F[+_]](factory: ApiFunctionFactory[F], errorFactory: ApiError.HandlerFailure => F[Nothing]) {
    def withUser[T](f: (State, User) => Future[F[T]]): ApiFunction[T] = factory { state =>
      state.auth match {
        case Some(auth) => f(state, auth.user)
        case None => Future.successful(errorFactory(ApiError.Unauthorized))
      }
    }

    def withUserOrImplicit[T](f: (State, User) => Future[F[T]]): ApiFunction[T] = withUser(f).redirectWithEvents { state =>
      state.auth match {
        case Some(auth) => Future.successful(Seq.empty)
        case None => implicitAuth.map(auth => Seq(ApiEvent.LoggedIn(auth.toAuthentication)))
      }
    }
  }

  implicit class GuardedAction(factory: Action.type) extends GuardedOps[ApiData.Action](factory, Returns.error)
  implicit class GuardedEffect(factory: Effect.type) extends GuardedOps[ApiData.Effect](factory, Returns.error)
}

object GuardDsl {
  def apply(jwt: JWT, db: Db)(implicit ec: ExecutionContext): GuardDsl =
    new GuardDsl(() => db.user.createImplicitUser().map(user => jwt.generateAuthentication(user)))
}
