package wust.backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import wust.api._
import auth._

class AuthApiImpl extends AuthApi {
  private def authentication(user: User) = {
    val (expires, token) = JWT.generateToken(user)
    Authentication(user, expires, token)
  }

  def register(name: String, password: String): Future[Option[Authentication]] =
    Db.user(name, password).map(_.map(authentication))

  def login(name: String, password: String): Future[Option[Authentication]] =
    Db.user.get(name, password).map(_.map(authentication))
}
