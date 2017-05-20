package wust.backend

import wust.api._
import wust.backend.DbConversions._
import wust.backend.auth._
import wust.framework.state._
import wust.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthApiImpl(holder: StateHolder[State, ApiEvent], dsl: GuardDsl, db: Db) extends AuthApi {
  import holder._, dsl._

  private def applyAuthenticationOnState(state: State, auth: Future[Option[JWTAuthentication]]): Future[State] = auth.map {
    case auth@Some(_) => state.copy(auth = auth)
    case None => State.initial
  }

  def register(name: String, password: String): Future[Boolean] = { (state: State) =>
    val (auth, success) = state.auth.map(_.user) match {
      case Some(user) if user.isImplicit =>
        val activated = db.user.activateImplicitUser(user.id, name, password).map(_.map(u => JWT.generateAuthentication(u)))
        (activated.map(_.orElse(state.auth)), activated.map(_.isDefined))
      case _ =>
        val newAuth = db.user(name, password).map(_.map(u => JWT.generateAuthentication(u)))
        (newAuth, newAuth.map(_.isDefined))
    }

    StateEffect(applyAuthenticationOnState(state, auth), success)
  }

  def login(name: String, password: String): Future[Boolean] = { (state: State) =>
    val auth = db.user.get(name, password).map(_.map(u => JWT.generateAuthentication(u)))
    StateEffect(applyAuthenticationOnState(state, auth), auth.map(_.isDefined))
  }

  def loginToken(token: Authentication.Token): Future[Boolean] = { (state: State) =>
    val auth = JWT.authenticationFromToken(token).map { auth =>
      for (valid <- db.user.checkIfEqualUserExists(auth.user))
        yield if (valid) Option(auth) else None
    }.getOrElse(Future.successful(None))

    StateEffect(applyAuthenticationOnState(state, auth), auth.map(_.isDefined))
  }

  def logout(): Future[Boolean] = { (state: State) =>
    val auth = Future.successful(None)
    StateEffect(applyAuthenticationOnState(state, auth), Future.successful(true))
  }
}
