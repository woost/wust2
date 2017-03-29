package wust.util

import org.scalatest._
import algorithm._

class AutoIdSpec extends FreeSpec with MustMatchers {
  "default behavior" in {
    val nextId = AutoId()
    nextId() mustEqual 0
    nextId() mustEqual 1
    nextId() mustEqual 2
  }

  "custom start" in {
    val nextId = AutoId(start = 17)
    nextId() mustEqual 17
    nextId() mustEqual 18
    nextId() mustEqual 19
  }

  "custom delta" in {
    val nextId = AutoId(delta = -1)
    nextId() mustEqual 0
    nextId() mustEqual -1
    nextId() mustEqual -2
  }
}
