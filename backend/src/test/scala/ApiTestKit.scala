package wust.backend

import org.scalatest._
import wust.backend.auth._
import wust.api._
import wust.db.Db
import wust.ids._
import wust.graph._
import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.mutable
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar

trait SpecsLikeMockito extends MockitoSugar {
  class MockitoMock[T](method: T) {
    def returns(value:T) = Mockito.when(method).thenReturn(value)
  }

  implicit def call2Mock[T](call: T): MockitoMock[T] = new MockitoMock[T](call)
}

trait ApiTestKit extends SpecsLikeMockito {
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

  def mockedDb = {
    val db = mock[Db]
    db.group returns mock[db.group.type]
    db.user returns mock[db.user.type]
    db.post returns mock[db.post.type]
    db.connection returns mock[db.connection.type]
    db.containment returns mock[db.containment.type]
    db
  }

  def mockDb[T](f: Db => T) = f(mockedDb)

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

