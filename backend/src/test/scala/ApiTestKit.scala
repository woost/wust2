package wust.backend

import wust.api._
import wust.backend.auth._
import wust.db.Db
import wust.framework.state._

import scala.concurrent.{ExecutionContext, Future}

trait ApiTestKit extends DbMocks {
  private def newStateHolder[T](state: State) = {
    new StateHolder[State, ApiEvent](Future.successful(state))
  }

  private def onResult[T](result: Future[T], holder: StateHolder[State, ApiEvent])(implicit ec: ExecutionContext): Future[(State, Seq[ApiEvent], T)] = {
    for {
      afterState <- holder.state
      events <- holder.events
      result <- result
    } yield (afterState, events, result)
  }

  def onAuthApi[T](state: State, jwt: JWT, db: Db = mockedDb, enableImplicit: Boolean = false)(f: AuthApi => Future[T])(implicit ec: ExecutionContext): Future[(State, Seq[ApiEvent], T)] = {
    val holder = newStateHolder(state)
    val impl = new AuthApiImpl(holder, GuardDsl(jwt, db, enableImplicit), db, jwt)
    onResult(f(impl), holder)
  }

  def onApi[T](state: State, jwt: JWT, db: Db = mockedDb, enableImplicit: Boolean = false)(f: Api => Future[T])(implicit ec: ExecutionContext): Future[(State, Seq[ApiEvent], T)] = {
    val holder = newStateHolder(state)
    val impl = new ApiImpl(holder, GuardDsl(jwt, db, enableImplicit), db)
    onResult(f(impl), holder)
  }
}

