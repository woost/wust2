package wust.backend

import wust.api._
import wust.backend.auth._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthApiImpl(apiAuth: AuthenticatedAccess) extends AuthApi {
  import apiAuth._

  def register(name: String, password: String): Future[Option[Authentication]] = {
    val auth = withUserOpt {
      case Some(user) if user.isImplicit =>
        Db.user.activateImplicitUser(user.id, name, password).map(_.map(JWT.generateAuthentication))
      case _ =>
        Db.user(name, password).map(_.map(JWT.generateAuthentication))
    }
    setAuth(auth)
    auth.map(_.map(_.toAuthentication))
  }

  def login(name: String, password: String): Future[Option[Authentication]] = {
    val auth = Db.user.get(name, password).map(_.map(JWT.generateAuthentication))
    setAuth(auth)
    auth.map(_.map(_.toAuthentication))
  }

  def loginToken(token: Authentication.Token): Future[Option[Authentication]] = {
    val auth = JWT.authenticationFromToken(token).map { auth =>
      for (valid <- Db.user.checkEqualUserExists(auth.user))
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
