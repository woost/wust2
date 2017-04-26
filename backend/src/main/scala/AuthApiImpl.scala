package wust.backend

import wust.api._
import wust.backend.auth._
import wust.db
import wust.backend.dbConversions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthApiImpl(apiAuth: AuthenticatedAccess) extends AuthApi {
  import apiAuth._

  def register(name: String, password: String): Future[Option[Authentication]] = {
    val auth = withUserOpt {
      case Some(user) if user.isImplicit =>
        db.user.activateImplicitUser(user.id, name, password).map(_.map(u => JWT.generateAuthentication(u)))
      case _ =>
        db.user(name, password).map(_.map(u => JWT.generateAuthentication(u)))
    }
    setAuth(auth)
    auth.map(_.map(_.toAuthentication))
  }

  def login(name: String, password: String): Future[Option[Authentication]] = {
    val auth = db.user.get(name, password).map(_.map(u => JWT.generateAuthentication(u)))
    setAuth(auth)
    auth.map(_.map(_.toAuthentication))
  }

  def loginToken(token: Authentication.Token): Future[Option[Authentication]] = {
    val auth = JWT.authenticationFromToken(token).map { auth =>
      for (valid <- db.user.checkEqualUserExists(auth.user))
        yield if (valid) Option(auth) else None
    }.getOrElse(Future.successful(None))
    setAuth(auth)
    auth.map(_.map(_.toAuthentication))
  }

  def logout(): Future[Boolean] = {
    setAuth(Future.successful(None))
    Future.successful(true)
  }
}
