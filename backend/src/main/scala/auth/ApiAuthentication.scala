package wust.backend.auth

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import wust.api._
import wust.util.Pipe

// api authentication wraps the authentication and is instantiated with the current auth on each request
class ApiAuthentication(auth: Future[Option[Authentication]], createImplicitAuth: () => Future[Option[Authentication]], toError: => Exception) {
  val actualAuth: Future[Option[Authentication]] =
    auth.map(_.filterNot(JWT.isExpired))

  private var _createdOrActualAuth: Future[Option[Authentication]] = actualAuth
  def createdOrActualAuth = _createdOrActualAuth

  lazy val actualOrImplicitAuth: Future[Option[Authentication]] = {
    val newAuth = auth.flatMap {
      case Some(auth) => Future.successful(Option(auth))
      case None => createImplicitAuth()
    }.map(_.filterNot(JWT.isExpired))

    _createdOrActualAuth = newAuth
    newAuth
  }

  def withUserOpt[T](f: Option[User] => Future[T]): Future[T] =
    actualAuth.flatMap(_.map(_.user) |> f)

  private def userAuthOrFail[T](auth: Future[Option[Authentication]])(f: User => Future[T]): Future[T] =
    auth.flatMap(_.map(_.user |> f).getOrElse(Future.failed(toError)))

  def withUser[T](f: User => Future[T]): Future[T] = userAuthOrFail(actualAuth)(f)
  def withUserOrImplicit[T](f: User => Future[T]): Future[T] = userAuthOrFail(actualOrImplicitAuth)(f)
  def withUser[T](f: => Future[T]): Future[T] = withUser(_ => f)
  def withUserOrImplicit[T](f: => Future[T]): Future[T] = withUserOrImplicit(_ => f)
}
