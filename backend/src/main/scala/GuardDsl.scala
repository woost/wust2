package wust.backend

import wust.api._
import wust.db.Db
import wust.backend.auth._
import wust.graph._
import DbConversions._

import scala.concurrent.{ ExecutionContext, Future }

//TODO instance for each client?
class GuardDsl(createImplicitAuth: () => Future[JWTAuthentication])(implicit ec: ExecutionContext) {
  private lazy val implicitAuth = createImplicitAuth()

  private def actualOrImplicitAuth(auth: Option[JWTAuthentication]): (Future[JWTAuthentication], Boolean) = auth match {
    case None => (implicitAuth, true)
    case Some(auth) => (Future.successful(auth), false)
  }

  def withUser[T](f: ApiResult.Function2[State, User, T]): ApiResult.Function[T] = state => {
    state.auth.fold[ApiResult[T]](ApiCall.fail[T](ApiError.Unauthorized))(auth => f(state, auth.user))
  }

  def withUserOrImplicit[T](code: ApiResult.Function3[State, User, Boolean, T]): ApiResult.Function[T] = state => {
    val (auth, wasCreated) = actualOrImplicitAuth(state.auth)
    for {
      auth <- auth
      newState = state.copy(auth = Some(auth))
    } yield {
      val result = code(newState, auth.user, wasCreated)
      val nextState = result.stateOps match {
        case ApiResult.ReplaceState(replacedState) => replacedState
        case ApiResult.KeepState => newState
      }

      ApiResult(state, result.call)
    }
  }
}

object GuardDsl {
  def apply(jwt: JWT, db: Db)(implicit ec: ExecutionContext): GuardDsl =
    new GuardDsl(() => db.user.createImplicitUser().map(user => jwt.generateAuthentication(user)))
}
