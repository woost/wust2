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

trait DbMocks extends SpecsLikeMockito {
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
}
