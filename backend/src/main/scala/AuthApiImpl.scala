package backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import api._, auth._

class AuthApiImpl extends AuthApi {
  private def authentication(user: User) = Authentication(user, JWTOps.generateToken(user))

  def register(name: String, password: String): Future[Option[Authentication]] =
    Db.user(name, password).map(_.map(authentication))

  def login(name: String, password: String): Future[Option[Authentication]] =
    Db.user.get(name, password).map(_.map(authentication))
}
