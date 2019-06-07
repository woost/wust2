package wust.backend

import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import wust.db.Db

import scala.concurrent.Future

trait SpecsLikeMockito extends MockitoSugar {
  class MockitoMock[T](method: T) {
    def returns(value:T) = Mockito.when(method).thenReturn(value)
  }

  class MockitoMockFuture[T](method: Future[T]) {
    def returnsFuture(value:T) = Mockito.when(method).thenReturn(Future.successful(value))
  }

  implicit def call2Mock[T](call: T): MockitoMock[T] = new MockitoMock[T](call)
  implicit def call2MockFuture[T](call: Future[T]): MockitoMockFuture[T] = new MockitoMockFuture[T](call)
}

trait DbMocks extends SpecsLikeMockito {
  def mockedDb = {
    val db = mock[Db]
    db.user returns mock[db.user.type]
    db.node returns mock[db.node.type]
    db.edge returns mock[db.edge.type]
    db.graph returns mock[db.graph.type]
    db
  }

  def mockDb[T](f: Db => T) = f(mockedDb)
}
