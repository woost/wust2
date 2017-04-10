package wust.backend.auth

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import wust.api._
import wust.framework.ConnectionAuth
import wust.util.Pipe

class ApiAuthentication(connectionAuth: ConnectionAuth[Authentication], toError: => Exception) {

  def actualAuth: Future[Option[Authentication]] =
    connectionAuth.auth.map(_.filterNot(JWT.isExpired))
  def actualOrAnonAuth: Future[Option[Authentication]] =
    connectionAuth.withImplicitAuth.map(_.filterNot(JWT.isExpired))

  def userAuthOrFail[T](auth: Future[Option[Authentication]])(f: User => Future[T]): Future[T] =
    auth.flatMap(_.map(_.user |> f).getOrElse(Future.failed(toError)))

  def withUserOpt[T](f: Option[User] => Future[T]): Future[T] =
    actualAuth.flatMap(_.map(_.user) |> f)

  def withUser[T](f: User => Future[T]): Future[T] = userAuthOrFail(actualAuth)(f)
  def withUserOrAnon[T](f: User => Future[T]): Future[T] = userAuthOrFail(actualOrAnonAuth)(f)

  def withUser[T](f: => Future[T]): Future[T] = withUser(_ => f)
  def withUserOrAnon[T](f: => Future[T]): Future[T] = withUserOrAnon(_ => f)
}
