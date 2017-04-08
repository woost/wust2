package wust.backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import wust.api._
import wust.util.Pipe
import auth._

class ApiAuthentication(authentication: Future[Option[Authentication]], setAuth: Future[Option[Authentication]] => Any) {

  private val enableImplicitAuth: Boolean = true //TODO config
  private def createImplicitAuth(): Future[Option[Authentication]] = {
    if (enableImplicitAuth) Db.user.createImplicitUser().map(JWT.generateAuthentication).map(Option.apply)
    else Future.successful(None)
  }

  private def implicitAuth() = createImplicitAuth() ||> setAuth

  def actualAuth: Future[Option[Authentication]] =
    authentication.map(_.filterNot(JWT.isExpired))
  def actualOrAnonAuth: Future[Option[Authentication]] =
    authentication.flatMap {
      case Some(auth) => Future.successful(Option(auth))
      case None => implicitAuth()
    }.map(_.filterNot(JWT.isExpired))

  def withUserAuth[T](auth: Future[Option[Authentication]])(f: User => Future[T]): Future[T] =
    auth.flatMap(_.map(_.user |> f).getOrElse(
      Future.failed(UserError(Unauthorized))
    ))

  def withUserOpt[T](f: Option[User] => Future[T]): Future[T] =
    actualAuth.flatMap(_.map(_.user) |> f)

  def withUser[T](f: User => Future[T]): Future[T] = withUserAuth(actualAuth)(f)
  def withUserOrAnon[T](f: User => Future[T]): Future[T] = withUserAuth(actualOrAnonAuth)(f)

  def withUser[T](f: => Future[T]): Future[T] = withUser(_ => f)
  def withUserOrAnon[T](f: => Future[T]): Future[T] = withUserOrAnon(_ => f)
}

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
