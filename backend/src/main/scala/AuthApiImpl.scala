package wust.backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import wust.api._
import auth._

class AuthApiImpl extends AuthApi {
  //TODO: get token from router function of request handler. we have the current user in the connected client, which is passed on.
  //      BUT currently this would create a new anon user.
  def registerImplicit(name: String, password: String, token: Authentication.Token): Future[Option[Authentication]] =
    JWT.authenticationFromToken(token)
      .map(auth => Db.user.activateImplicitUser(auth.user.id, name, password).map(_.map(JWT.generateAuthentication)))
      .getOrElse(Future.successful(None))

  def register(name: String, password: String): Future[Option[Authentication]] =
    Db.user(name, password).map(_.map(JWT.generateAuthentication))

  def login(name: String, password: String): Future[Option[Authentication]] =
    Db.user.get(name, password).map(_.map(JWT.generateAuthentication))
}
