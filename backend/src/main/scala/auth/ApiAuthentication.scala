package wust.backend.auth

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import wust.api._
import wust.framework.ConnectionAuth
import wust.util.Pipe

class ApiAuthentication(connectionAuth: ConnectionAuth[Authentication], toError: => Exception) {
  def actualAuth: Future[Option[Authentication]] =
    connectionAuth.auth.map(_.filterNot(JWT.isExpired))

  def actualOrImplicitAuth: Future[Option[Authentication]] =
    connectionAuth.authOrImplicit.map(_.filterNot(JWT.isExpired))

  def withUserOpt[T](f: Option[User] => Future[T]): Future[T] =
    actualAuth.flatMap(_.map(_.user) |> f)

  private def userAuthOrFail[T](auth: Future[Option[Authentication]])(f: User => Future[T]): Future[T] =
    auth.flatMap(_.map(_.user |> f).getOrElse(Future.failed(toError)))

  def withUser[T](f: User => Future[T]): Future[T] = userAuthOrFail(actualAuth)(f)
  def withUserOrImplicit[T](f: User => Future[T]): Future[T] = userAuthOrFail(actualOrImplicitAuth)(f)
  def withUser[T](f: => Future[T]): Future[T] = withUser(_ => f)
  def withUserOrImplicit[T](f: => Future[T]): Future[T] = withUserOrImplicit(_ => f)
}
