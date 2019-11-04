package wust.core

import wust.api._
import wust.core.DbConversions._
import wust.core.Dsl._
import wust.core.auth.JWT
import wust.db.{Db, Data}
import wust.ids._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class GuardDsl(jwt: JWT, db: Db)(implicit ec: ExecutionContext) {

  private def createImplicitAuth(
      user: AuthUser.Assumed
  ): Future[Option[Authentication.Verified]] = {
    db.ctx.transaction { implicit ctx =>
      db.user
        .createImplicitUser(user.id, user.name)
        .map(user => Some(jwt.generateAuthentication(user)))
    }
  }

  implicit class GuardedOps[F[+ _]: ApiData.MonadError](factory: ApiFunction.Factory[F]) {
    private def requireUserT[T, U <: AuthUser](
        f: (State, U) => Future[F[T]],
    )(userf: PartialFunction[AuthUser, U]): ApiFunction[T] = factory { state =>
      state.auth
        .map(_.user)
        .collect(userf andThen (f(state, _)))
        .getOrElse(Future.successful(ApiData.MonadError.raiseError(ApiError.Unauthorized)))
    }

    def requireUser[T](f: (State, AuthUser) => Future[F[T]]): ApiFunction[T] =
      requireUserT[T, AuthUser](f) { case u => u }
    def requireImplicitUser[T](f: (State, AuthUser.Implicit) => Future[F[T]]): ApiFunction[T] =
      requireUserT[T, AuthUser.Implicit](f) { case u: AuthUser.Implicit => u }
    def requireAssumedUser[T](f: (State, AuthUser.Assumed) => Future[F[T]]): ApiFunction[T] =
      requireUserT[T, AuthUser.Assumed](f) { case u: AuthUser.Assumed => u }
    def requireRealUser[T](f: (State, AuthUser.Real) => Future[F[T]]): ApiFunction[T] =
      requireUserT[T, AuthUser.Real](f) { case u: AuthUser.Real => u }
    def requireDbUser[T](f: (State, AuthUser.Persisted) => Future[F[T]]): ApiFunction[T] =
      requireUserT[T, AuthUser.Persisted](f) { case u: AuthUser.Persisted => u }
    def requireEmail[T](f: (State, AuthUser.Persisted, String) => Future[F[T]]): ApiFunction[T] =
      requireDbUser { (state, user) =>
        db.user.getUserDetail(user.id).flatMap {
          case Some(Data.UserDetail(_, Some(email), true, _)) => f(state, user, email)
          case _ => Future.successful(ApiData.MonadError.raiseError(ApiError.Unauthorized))
        }
      }

    def assureDbUserIf[T](condition: Boolean)(f: (State, AuthUser) => Future[F[T]]): ApiFunction[T] =
      if (condition) assureDbUser(f) else requireUser(f)

    def assureDbUser[T](f: (State, AuthUser.Persisted) => Future[F[T]]): ApiFunction[T] =
      ApiFunction.redirect(requireDbUser(f)) { state =>
        val auth = state.auth.collect {
          case Authentication.Assumed(user) => createImplicitAuth(user)
        } getOrElse Future.successful(None)

        auth.map(auth => auth.map(ApiEvent.LoggedIn(_)).toSeq)
      }
  }

  def validAuthFromToken(token: Authentication.Token)(implicit ec: ExecutionContext): Future[Option[Authentication.Verified]] =
    jwt.authenticationFromToken(token).map { auth =>
      //TODO: we anyhow go to the db for each authentication check, so we could also switch to sessions whcih can be stopped and controlled in a better way. furthermore auth tokens can be smaller.
      db.user.checkIfEqualUserExists(auth.user).map {
        case Some(user) => Some(auth.copy(user = forClientAuth(user)))
        case None => None
      }
    } getOrElse Future.successful(None)

  def onBehalfOfUser[T, F[_]: ApiData.MonadError](token: Authentication.Token)(code: Authentication.Verified => Future[F[T]])(implicit ec: ExecutionContext): Future[F[T]] = {
    val newAuth = validAuthFromToken(token)
    newAuth.flatMap(
      _.fold[Future[F[T]]](Future.successful(ApiData.MonadError.raiseError(ApiError.Forbidden)))(
        code
      )
    )
  }

  def canAccessNode[T, F[_]: ApiData.MonadError](userId: UserId, nodeId: NodeId )(code: => Future[F[T]])(implicit ec: ExecutionContext): Future[F[T]] = {
    (for {
      true <- db.user.canAccessNode(userId, nodeId)
      result <- code
    } yield result).recover {
      case NonFatal(_) => ApiData.MonadError.raiseError(ApiError.Forbidden)
    }
  }
}
