package wust.framework

import org.scalatest.concurrent.ScalaFutures
import concurrent.Promise
import org.scalatest._

class OpenRequestsSpec extends FreeSpec with MustMatchers with ScalaFutures {
  "timeout promise" - {
    "timeouts after some time" ignore { // TODO
      val promise = TimeoutPromise[Int](10)
      whenReady(promise.future.failed)(_ mustEqual TimeoutException)
    }

    "not timeout directly" in {
      val promise = TimeoutPromise[Int](10)
      promise success 1
      promise.future.value.flatMap(_.toOption) mustEqual Some(1)
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
