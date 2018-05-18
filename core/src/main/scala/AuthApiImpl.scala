package wust.backend

import com.roundeights.hasher.Hasher
import wust.api._
import wust.graph.{Graph, Post, User}
import wust.ids._
import wust.backend.Dsl._
import wust.backend.DbConversions._
import wust.backend.auth._
import wust.db.Db

import scala.concurrent.{ExecutionContext, Future}

class AuthApiImpl(dsl: GuardDsl, db: Db, jwt: JWT)(implicit ec: ExecutionContext) extends AuthApi[ApiFunction] {
  import dsl._

  def register(name: String, password: String): ApiFunction[Boolean] = Effect { state =>
    val digest = passwordDigest(password)
    val newUser = state.auth.user match {
      case User.Implicit(prevUserId, _, _, _) =>
        //TODO: propagate name change to the respective groups
        db.user.activateImplicitUser(prevUserId, name, digest)
      case User.Assumed(userId) => db.user(userId, name, digest)
      case _ => db.user(UserId.fresh, name, digest)
    }

    val newAuth = newUser.map(_.map(u => jwt.generateAuthentication(u)))
    resultOnVerifiedAuth(newAuth)
  }

  def login(name: String, password: String): ApiFunction[Boolean] = Effect { state =>
    val digest = passwordDigest(password)
    val newUser = db.user.getUserAndDigest(name).flatMap {
      case Some((user, userDigest)) if (digest.hash = userDigest) =>
        state.auth.dbUserOpt match {
          case Some(User.Implicit(prevUserId, _, _, _)) =>
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
    val newAuth = validAuthFromToken(token)
    resultOnVerifiedAuth(newAuth)
  }

  def verifyToken(token: Authentication.Token): ApiFunction[Option[Authentication.Verified]] = Action {
    validAuthFromToken(token)
  }

  def assumeLogin(userId: UserId): ApiFunction[Boolean] = Effect { state =>
    val newAuth = Authentication.Assumed(User.Assumed(userId))
    resultOnAssumedAuth(newAuth)
  }

  def logout(): ApiFunction[Boolean] = Effect { state =>
    val newAuth = Authentication.Assumed.fresh
    resultOnAssumedAuth(newAuth)
  }

  def issuePluginToken(): ApiFunction[Authentication.Verified] = Action.assureDbUser { (_, user) =>
    //TODO generate special token for plugins to allow onBehalf changes
    Future.successful(jwt.generateAuthentication(user))
  }

  private def passwordDigest(password: String) = Hasher(password).bcrypt

  private def authChangeEvents(auth: Authentication): Future[Seq[ApiEvent]] = {
    val authEvent = auth match {
      case auth: Authentication.Assumed => ApiEvent.AssumeLoggedIn(auth)
      case auth: Authentication.Verified => ApiEvent.LoggedIn(auth)
    }

    //TODO: If user is on a page, send the corresponding graph
    val graph = Future.successful(Graph.empty)

    graph.map {graph =>
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
}
