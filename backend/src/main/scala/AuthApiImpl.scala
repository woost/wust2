package wust.backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import wust.api._
import auth._

class AuthApiImpl(apiAuth: ApiAuthentication) extends AuthApi {
  import apiAuth._

  def register(name: String, password: String): Future[Option[Authentication]] = actualAuth.flatMap {
    case Some(auth) if auth.user.isImplicit =>
      Db.user.activateImplicitUser(auth.user.id, name, password)
        .map(_.map(JWT.generateAuthentication))
    case _ =>
      Db.user(name, password)
        .map(_.map(JWT.generateAuthentication))
  }

  def login(name: String, password: String): Future[Option[Authentication]] =
    Db.user.get(name, password).map(_.map(JWT.generateAuthentication))
}
