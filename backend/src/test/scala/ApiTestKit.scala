package wust.backend

import wust.api._
import wust.backend.auth._
import wust.db.Db

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

trait ApiTestKit extends DbMocks {
  private def newStateAccess[T](state: State, implicitAuth: Option[JWTAuthentication]): (StateAccess, mutable.Seq[ChannelEvent]) = {
    val events = mutable.ArrayBuffer.empty[ChannelEvent]
    val access = new StateAccess(Future.successful(state), events += _, () => Future.successful(implicitAuth))
    (access, events)
  }

  private def onResult[API, T](impl: API, access: StateAccess, events: mutable.Seq[ChannelEvent])(f: API => Future[T])(implicit ec: ExecutionContext): Future[(State, Seq[ApiEvent], T)] = {
    val result = f(impl)
    for {
      afterState <- access.state
      result <- result
    } yield (afterState, events.map(_.event).toSeq, result)
  }

  def onAuthApi[T](state: State, db: Db = mockedDb, implicitAuth: JWTAuthentication = null)(f: AuthApi => Future[T])(implicit ec: ExecutionContext): Future[(State, Seq[ApiEvent], T)] = {
    val (access, events) = newStateAccess(state, Option(implicitAuth))
    val impl = new AuthApiImpl(access, db, TestDefaults.jwt)
    onResult(impl, access, events)(f)
  }

  def onApi[T](state: State, db: Db = mockedDb, implicitAuth: JWTAuthentication = null)(f: Api => Future[T])(implicit ec: ExecutionContext): Future[(State, Seq[ApiEvent], T)] = {
    val (access, events) = newStateAccess(state, Option(implicitAuth))
    val impl = new ApiImpl(access, db)
    onResult(impl, access, events)(f)
  }
}

