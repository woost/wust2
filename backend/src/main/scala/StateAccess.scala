package wust.backend

import wust.backend.config.Config
import wust.graph._
import wust.api._
import wust.backend.auth._
import wust.util.Pipe

import scala.concurrent.{Future, ExecutionContext}

object AuthHelper {
  import Config.auth.enableImplicit
  import wust.db.Db
  import DbConversions._

  def createImplicitAuth()(implicit ec: ExecutionContext): Future[JWTAuthentication] =
    Db.user.createImplicitUser().map(u => JWT.generateAuthentication(forClient(u)))

  def actualOrImplicitAuth(auth: Option[JWTAuthentication])(implicit ec: ExecutionContext): Future[Option[JWTAuthentication]] = auth match {
    case None if enableImplicit => createImplicitAuth().map(Option.apply)
    case auth => Future.successful(auth)
  }

  def userAuthOrFail[T](auth: Option[JWTAuthentication])(f: User => Future[T]): Future[T] =
    auth.map(_.user |> f).getOrElse(Future.failed(ApiException(Unauthorized)))
}

trait StateImplicits {
  import AuthHelper._

  implicit class RichState(state: State) {
    def withUserOpt[T](f: Option[User] => Future[T]): Future[T] = state.auth.map(_.user) |> f
    def withUser[T](f: User => Future[T]): Future[T] = userAuthOrFail(state.auth)(f)
    def withUser[T](f: => Future[T]): Future[T] = withUser(_ => f)
    def withUserOrImplicit[T](f: User => Future[T])(implicit ec: ExecutionContext): (Future[State], Future[T]) = {
      val auth = actualOrImplicitAuth(state.auth)
      val newState = auth.map(auth => state.copy(auth = auth))
      val result = auth.flatMap(userAuthOrFail(_)(f))
      (newState, result)
    }
    def withUserOrImplicit[T](f: => Future[T])(implicit ec: ExecutionContext): (Future[State], Future[T]) =
      withUserOrImplicit(_ => f)
  }
}
object StateImplicits extends StateImplicits

class StateAccess private(initialState: Future[State])(implicit ec: ExecutionContext) extends StateImplicits {
  private var actualState = initialState
  def state = actualState

  def withState[T](f: State => Future[T]): Future[T] = state.flatMap(f)
  def withStateChange[T](f: State => (Future[State], Future[T])): Future[T] = {
    val result = state.map(f)
    actualState = result.flatMap(_._1)
    result.flatMap(_._2)
  }
}
object StateAccess {
  def apply(state: Future[State])(implicit ec: ExecutionContext): StateAccess = {
    val validState = state.map(_.copyF(auth = _.filterNot(JWT.isExpired)))
    new StateAccess(validState)
  }
}
