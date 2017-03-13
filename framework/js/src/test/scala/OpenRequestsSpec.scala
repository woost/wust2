package wust.framework

import scala.concurrent.Promise
import org.scalatest._

class OpenRequestsSpec extends AsyncFreeSpec with MustMatchers {
  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  "timeout promise" - {
    //TODO: why does it need executionContext = global? also breaks grouping of tests in output
    "timeouts after some time" in {
      val promise = TimeoutPromise[Int](10)
      promise.future.failed.map(_ mustEqual TimeoutException)
    }

    "not timeout directly" in {
      val promise = TimeoutPromise[Int](10)
      promise success 1
      promise.future.map(_ mustEqual 1)
    }
  }

  "open requests" - {
    "unique sequence ids" in {
      val requests = new OpenRequests[Int](10)
      val (id1, _) = requests.open()
      val (id2, _) = requests.open()
      id1 must not equal id2
    }

    "get by id" in {
      val requests = new OpenRequests[Int](10)
      val (id, promise) = requests.open()
      requests.get(id) mustEqual Some(promise)
    }

    "get with non-existing" in {
      val requests = new OpenRequests[Int](10)
      requests.get(1) mustEqual None
    }

    "usable promise" in {
      val requests = new OpenRequests[Int](10)
      val (_, promise) = requests.open()
      promise success 1
      promise.future.value.flatMap(_.toOption) mustEqual Some(1)
    }
  }
}
