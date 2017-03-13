package wust.framework

import org.scalatest._
import boopickle.Default._

import message._

class AutowireServerSpec extends FreeSpec with MustMatchers {
  case class Something(i: Int)

  "write and read" in {
    val obj = Something(2)
    val serialized = AutowireServer.write(obj)
    val deserialized = AutowireServer.read[Something](serialized)
    deserialized mustEqual obj
  }
}
