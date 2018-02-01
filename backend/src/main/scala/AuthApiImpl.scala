package wust.backend

import com.roundeights.hasher.Hasher
import wust.api._
import wust.graph.User
import wust.ids._
import wust.backend.DbConversions._
import wust.backend.auth._
import wust.db.Db
import wust.util.RichFuture

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

  //TODO login only with minimum lifetime and auto disconnect of ws
  /*if !auth.isExpiredIn(minTokenLifetime)*/
class AuthApiImpl(dsl: GuardDsl, db: Db, jwt: JWT)(implicit ec: ExecutionContext) extends AuthApi[ApiFunction] {
  import dsl._

  private def passwordDigest(password: String) = Hasher(password).bcrypt

  private def authChangeEvents(auth: Authentication): Future[Seq[ApiEvent]] = {
    db.graph.getAllVisiblePosts(auth.dbUserOpt.map(_.id)).map { dbGraph =>
      val graph = forClient(dbGraph).consistent
      val authEvent = auth match {
        case auth: Authentication.Assumed => ApiEvent.AssumeLoggedIn(auth)
        case auth: Authentication.Verified => ApiEvent.LoggedIn(auth)
      }
      authEvent :: ApiEvent.ReplaceGraph(graph) :: Nil
    }
  }

  private def resultOnAssumedAuth(auth: Authentication.Assumed): Future[ApiData.Effect[Boolean]] = {
    authChangeEvents(auth).map(Returns(true, _))
  }

  private def resultOnVerifiedAuth(auth: Future[Option[Authentication.Verified]]): Future[ApiData.Effect[Boolean]] = auth.flatMap {
    case Some(auth) => authChangeEvents(auth).map(Returns(true, _))
    case _ => Future.successful(Returns(false))
  }

  def register(name: String, password: String): ApiFunction[Boolean] = Effect { state =>
    val digest = passwordDigest(password)
    val newUser = state.auth.dbUserOpt match {
      case Some(User.Implicit(prevUserId, _, _)) =>
        //TODO: propagate name change to the respective groups
        db.user.activateImplicitUser(prevUserId, name, digest)
      case _ => db.user(name, digest)
    }

    val newAuth = newUser.map(_.map(u => jwt.generateAuthentication(u)))
    resultOnVerifiedAuth(newAuth)
  }

  def login(name: String, password: String): ApiFunction[Boolean] = Effect { state =>
    val digest = passwordDigest(password)
    val newUser = db.user.getUserAndDigest(name).flatMap {
      case Some((user, userDigest)) if (digest.hash = userDigest) =>
        state.auth.dbUserOpt match {
          case Some(User.Implicit(prevUserId, _, _)) =>
            //TODO propagate new groups into state?
            //TODO: propagate name change to the respective groups and the connected clients
            db.user
              .mergeImplicitUser(prevUserId, user.id)
              .map(if (_) Some(user) else None)
          case _ => Future.successful(Some(user))
        }

      case _ => Future.successful(None)
    }

    val newAuth = newUser.map(_.map(u => jwt.generateAuthentication(u)))
    resultOnVerifiedAuth(newAuth)
  }

  def loginToken(token: Authentication.Token): ApiFunction[Boolean] = Effect { state =>
    val newAuth = jwt.authenticationFromToken(token).map { auth =>
      db.user.checkIfEqualUserExists(auth.user).map { isValid =>
        if (isValid) Some(auth) else None
      }
    } getOrElse Future.successful(None)

    resultOnVerifiedAuth(newAuth)
  }

  def assumeLogin(userId: UserId): ApiFunction[Boolean] = Effect { state =>
    val newAuth = Authentication.Assumed(User.Assumed(userId))
    resultOnAssumedAuth(newAuth)
  }

  def logout(): ApiFunction[Boolean] = Effect { state =>
    val newAuth = Authentication.Assumed.fresh
    resultOnAssumedAuth(newAuth)
  }
}
