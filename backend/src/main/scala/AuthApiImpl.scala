package wust.backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import wust.api._
import auth._

class AuthApiImpl extends AuthApi {
  def register(name: String, password: String): Future[Option[Authentication]] =
    Db.user(name, password).map(_.map(JWT.generateAuthentication))

  def login(name: String, password: String): Future[Option[Authentication]] =
    Db.user.get(name, password).map(_.map(JWT.generateAuthentication))
}
