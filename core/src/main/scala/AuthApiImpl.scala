package wust.backend

import com.roundeights.hasher.Hasher
import wust.api._
import wust.backend.DbConversions._
import wust.backend.Dsl._
import wust.backend.auth._
import wust.db.{Db, SuccessResult}
import wust.ids._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class AuthApiImpl(dsl: GuardDsl, db: Db, jwt: JWT, emailFlow: AppEmailFlow)(implicit ec: ExecutionContext) extends AuthApi[ApiFunction] {
  import dsl._

  def changePassword(password: String): ApiFunction[Boolean] = Effect.requireRealUser { (state, user) =>
    val digest = passwordDigest(password)
    db.user.changePassword(user.id, digest)
      .map(_ => Returns(true))
  }

  //TODO: some password checks?
  def register(name: String, email: String, password: String): ApiFunction[AuthResult] = Effect { state =>
    //TODO: we do not know from the result of user create what went wrong, it could either be already existing username or email. so we check email first once and then assume wrong username. would be gone if we remove unique username.
    val newAuth = db.user.existsEmail(email).flatMap { emailAlreadyExists =>
      if (emailAlreadyExists) Future.successful(Left(AuthResult.BadEmail))
      else {
        val digest = passwordDigest(password)
        val newUser = state.auth.map(_.user) match {
          case Some(AuthUser.Implicit(prevUserId, _, _)) =>
            //TODO: propagate name change to the respective groups
            db.ctx.transaction { implicit ec =>
              db.user.activateImplicitUser(prevUserId, name = name, email = email, passwordDigest = digest)
            }
          case Some(AuthUser.Assumed(userId)) =>
            db.user.create(userId, name = name, email = email, passwordDigest = digest).map(Some(_)).recover{case NonFatal(t) => None }
          case _ =>
            db.user.create(UserId.fresh, name = name, email = email, passwordDigest = digest).map(Some(_)).recover{case NonFatal(t) => None }
        }

        newUser.map(_.map { user =>
          sendEmailVerification(UserDetail(user.id, Some(email), verified = false))

          jwt.generateAuthentication(user)
        }.toRight(AuthResult.BadUser))
      }
    }

    resultOnVerifiedAuth(newAuth, AuthResult.Success)
  }

  def login(name: String, password: String): ApiFunction[AuthResult] = Effect { state =>
    val digest = passwordDigest(password)
    val newUser = db.user.getUserAndDigest(name).flatMap {
      case Some((user, userDigest)) if (digest.hash = userDigest) =>
        state.auth.flatMap(_.dbUserOpt) match {
          case Some(AuthUser.Implicit(prevUserId, _, _)) =>
            //TODO propagate new groups into state?
            //TODO: propagate name change to the respective groups and the connected clients
            db.ctx.transaction { implicit ec =>
              db.user.mergeImplicitUser(prevUserId, user.id).flatMap {
                case true => Future.successful(Right(user))
                case false =>
                  Future.failed(
                    new Exception(
                      s"Failed to merge implicit user ($prevUserId) into real user (${user.id})"
                    )
                  )
              }
            }
          case _ => Future.successful(Right(user))
        }

      case Some(_) => Future.successful(Left(AuthResult.BadPassword))
      case None    => Future.successful(Left(AuthResult.BadUser))
    }

    val newAuth = newUser.map(_.map(u => jwt.generateAuthentication(u)))
    resultOnVerifiedAuth(newAuth, AuthResult.Success)
  }

  def loginToken(token: Authentication.Token): ApiFunction[Boolean] = Effect { state =>
    val newAuth = validAuthFromToken(token)
    resultOnVerifiedAuth(newAuth)
  }

  def verifyToken(token: Authentication.Token): ApiFunction[Option[Authentication.Verified]] = Action {
    validAuthFromToken(token)
  }

  def assumeLogin(user: AuthUser.Assumed): ApiFunction[Boolean] = Effect { _ =>
    db.user.checkIfUserAlreadyExists(user.id).map { alreadyExists =>
      resultOnAssumedAuth(!alreadyExists, Authentication.Assumed(user))
    }
  }

  def logout(): ApiFunction[Boolean] = Effect { _ =>
    val newAuth = Authentication.Assumed.fresh
    Future.successful(resultOnAssumedAuth(true, newAuth))
  }

  def createImplicitUserForApp(): ApiFunction[Option[Authentication.Verified]] = Action { _ =>
    val userId = UserId.fresh
    val implUser = db.user.createImplicitUser(userId, userId.toBase58)
    implUser.map { auth =>
      Some(jwt.generateAuthentication(auth))
    }
  }

  def issuePluginToken(): ApiFunction[Authentication.Verified] = Action.assureDbUser { (_, user) =>
    //TODO generate special token for plugins to allow onBehalf changes
    Future.successful(jwt.generateAuthentication(user))
  }

  override def getUserDetail(userId: UserId): ApiFunction[Option[UserDetail]] = Action.requireUser { (_, user) =>
    user match {
      case user: AuthUser.Assumed => Future.successful(Returns(None))
      case user: AuthUser.Persisted if user.id == userId => db.user.getUserDetail(user.id).map(detail => Returns(detail.map(forClient(_)))) // currently only allow to access own user details
      case _ => Future.successful(Returns.error(ApiError.Forbidden))
    }
  }

  override def updateUserEmail(userId: UserId, newEmail: String): ApiFunction[Boolean] = Action.requireRealUser { (_, user) =>
    if (userId == user.id) { // currently only allow to change own user details
      val result = db.user.updateUserEmail(user.id, newEmail)

      result.map { case success =>
        if (success) sendEmailVerification(UserDetail(userId, Some(newEmail), verified = false))
        Returns(success)
      }
    } else Future.successful(Returns.error(ApiError.Forbidden))
  }

  override def resendEmailVerification(userId: UserId): ApiFunction[Unit] = Action.requireRealUser { (_, user) =>
    if (userId == user.id) { // currently only allow to change own user details
      db.user.getUserDetail(user.id).map {
        case Some(userDetail) => sendEmailVerification(userDetail)
        case None => ()
      }
    } else Future.successful(Returns.error(ApiError.Forbidden))
  }

  private def sendEmailVerification(userDetail: UserDetail): Unit = if (!userDetail.verified) userDetail.email.foreach { email =>
    emailFlow.sendEmailVerification(userDetail.userId, email)
  }

  private def passwordDigest(password: String) = Hasher(password).bcrypt

  private def authChangeEvents(auth: Authentication): Seq[ApiEvent] = {
    val authEvent = auth match {
      case auth: Authentication.Assumed  => ApiEvent.AssumeLoggedIn(auth)
      case auth: Authentication.Verified => ApiEvent.LoggedIn(auth)
    }

    authEvent :: Nil
  }

  private def resultOnAssumedAuth(success: Boolean, auth: Authentication.Assumed): ApiData.Effect[Boolean] = {
    if (success) Returns(true, authChangeEvents(auth)) else Returns(false)
  }

  private def resultOnVerifiedAuth[T](
      auth: Future[Either[T, Authentication.Verified]],
      positiveValue: T
  ): Future[ApiData.Effect[T]] = auth.map {
    case Right(auth) => Returns(positiveValue, authChangeEvents(auth))
    case Left(err)   => Returns(err)
  }

  private def resultOnVerifiedAuth(
      auth: Future[Option[Authentication.Verified]]
  ): Future[ApiData.Effect[Boolean]] = auth.map {
    case Some(auth) => Returns(true, authChangeEvents(auth))
    case _          => Returns(false)
  }
}
