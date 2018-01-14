package wust.backend

import com.roundeights.hasher.Hasher
import wust.api._
import wust.backend.DbConversions._
import wust.backend.auth._
import wust.db.Db
import wust.util.RichFuture

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

//TODO should send auth events here?
class AuthApiImpl(dsl: GuardDsl, db: Db, jwt: JWT, minTokenLifetime: Duration)(implicit ec: ExecutionContext) extends AuthApi[ApiFunction] {
  import dsl._

  private def passwordDigest(password: String) = Hasher(password).bcrypt

  private def resultOnAuth(state: State, auth: Future[Option[JWTAuthentication]]): Future[ApiData.Effect[Boolean]] = auth.map {
    //TODO minimum lifetime and auto disconnect of ws
    case Some(auth) /*if !auth.isExpiredIn(minTokenLifetime)*/ => Returns(state.copy(auth = Some(auth)), true)
    case _ => Returns(state, false)
  }

  def register(name: String, password: String): ApiFunction[Boolean] = Effect { state =>
    val digest = passwordDigest(password)
    val newUser = state.auth.map(_.user) match {
      case Some(user) if user.isImplicit =>
        //TODO: propagate name change to the respective groups
        db.user.activateImplicitUser(user.id, name, digest)
      case _ => db.user(name, digest)
    }

    val newAuth = newUser.map(_.map(u => jwt.generateAuthentication(u)))
    resultOnAuth(state, newAuth)
  }

  def login(name: String, password: String): ApiFunction[Boolean] = Effect { state =>
    val digest = passwordDigest(password)
    val newAuth = db.user.getUserAndDigest(name).map {
      case Some((user, userDigest)) if (digest.hash = userDigest) =>
        //TODO integrate result into response?
        state.auth
          .filter(_.user.isImplicit)
          .foreach { auth =>
            //TODO propagate new groups into state?
            //TODO: propagate name change to the respective groups and the connected clients
            if (auth.user.isImplicit) db.user.mergeImplicitUser(auth.user.id, user.id).log
          }

        Some(jwt.generateAuthentication(user))
      case _ => None
    }

    resultOnAuth(state, newAuth)
  }

  def loginToken(token: Authentication.Token): ApiFunction[Boolean] = Effect { state =>
    val newAuth = jwt.authenticationFromToken(token).map { auth =>
      db.user.checkIfEqualUserExists(auth.user).map { isValid =>
        if (isValid) Some(auth) else None
      }
    } getOrElse Future.successful(None)

    resultOnAuth(state, newAuth)
  }

  def logout(): ApiFunction[Boolean] = Effect { state =>
    Future { Returns(state.copy(auth = None), true) }
  }
}
