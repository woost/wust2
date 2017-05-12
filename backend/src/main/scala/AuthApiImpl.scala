package wust.backend

import wust.api._
import wust.backend.auth._
import wust.backend.DbConversions._
import wust.db.Db
import wust.ids._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthApiImpl(stateAccess: StateAccess, db: Db) extends AuthApi {
  import stateAccess._

  private def applyAuthenticationOnState(state: State, auth: Future[Option[JWTAuthentication]]): Future[State] = {
    auth.flatMap {
      case Some(auth) =>
        db.group.memberships(auth.user.id) .map(_.map(_.groupId).toSet).map { groupIds =>
          state.copy(auth = Option(auth), groupIds = groupIds)
        }
      case None => Future.successful(State.initial)
    }
  }

  def register(name: String, password: String): Future[Option[Authentication]] = { (state: State) =>
    val auth = state.auth.map(_.user) match {
      case Some(user) if user.isImplicit =>
        db.user.activateImplicitUser(user.id, name, password).map(_.map(u => JWT.generateAuthentication(u)))
      case _ =>
        db.user(name, password).map(_.map(u => JWT.generateAuthentication(u)))
    }

    StateEffect(applyAuthenticationOnState(state, auth), auth.map(_.map(_.toAuthentication)))
  }

  def login(name: String, password: String): Future[Option[Authentication]] = { (state: State) =>
    val auth = db.user.get(name, password).map(_.map(u => JWT.generateAuthentication(u)))
    StateEffect(applyAuthenticationOnState(state, auth), auth.map(_.map(_.toAuthentication)))
  }

  def loginToken(token: Authentication.Token): Future[Option[Authentication]] = { (state: State) =>
    val auth = JWT.authenticationFromToken(token).map { auth =>
      for (valid <- db.user.checkIfEqualUserExists(auth.user))
        yield if (valid) Option(auth) else None
    }.getOrElse(Future.successful(None))

    StateEffect(applyAuthenticationOnState(state, auth), auth.map(_.map(_.toAuthentication)))
  }

  def logout(): Future[Boolean] = { (state: State) =>
    val auth = Future.successful(None)
    StateEffect(applyAuthenticationOnState(state, auth), Future.successful(true))
  }
}
