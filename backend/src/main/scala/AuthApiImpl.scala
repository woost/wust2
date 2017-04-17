package wust.backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import wust.api._
import auth._

class AuthApiImpl(apiAuth: AuthenticatedAccess) extends AuthApi {
  import apiAuth._

  def register(name: String, password: String): Future[Option[Authentication]] = withUserOpt {
    case Some(user) if user.isImplicit =>
      Db.user.activateImplicitUser(user.id, name, password)
        .map(_.map(JWT.generateAuthentication).map(_.toAuthentication))
    case _ =>
      Db.user(name, password)
        .map(_.map(JWT.generateAuthentication).map(_.toAuthentication))
  }

  def login(name: String, password: String): Future[Option[Authentication]] =
    Db.user.get(name, password).map(_.map(JWT.generateAuthentication).map(_.toAuthentication))
}
