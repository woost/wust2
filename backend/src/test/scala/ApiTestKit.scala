package wust.backend

import wust.api._
import wust.backend.auth._
import wust.db.Db
import wust.framework.state._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

trait ApiTestKit extends DbMocks {
  private def newStateHolder[T](state: State): (StateHolder[State, ApiEvent], mutable.Seq[ApiEvent]) = {
    val events = mutable.ArrayBuffer.empty[ApiEvent]
    val holder = new StateHolder[State, ApiEvent](Future.successful(state), events += _)
    (holder, events)
  }

  private def onResult[API, T](impl: API, holder: StateHolder[State, ApiEvent], events: mutable.Seq[ApiEvent])(f: API => Future[T])(implicit ec: ExecutionContext): Future[(State, Seq[ApiEvent], T)] = {
    val result = f(impl)
    for {
      afterState <- holder.state
      result <- result
    } yield (afterState, events, result)
  }

  def onAuthApi[T](state: State, db: Db = mockedDb, enableImplicit: Boolean = false)(f: AuthApi => Future[T])(implicit ec: ExecutionContext): Future[(State, Seq[ApiEvent], T)] = {
    val (holder, events) = newStateHolder(state)
    val impl = new AuthApiImpl(holder, new GuardDsl(db, enableImplicit), db)
    onResult(impl, holder, events)(f)
  }

  def onApi[T](state: State, db: Db = mockedDb, enableImplicit: Boolean = false)(f: Api => Future[T])(implicit ec: ExecutionContext): Future[(State, Seq[ApiEvent], T)] = {
    val (holder, events) = newStateHolder(state)
    val impl = new ApiImpl(holder, new GuardDsl(db, enableImplicit), db)
    onResult(impl, holder, events)(f)
  }
}

