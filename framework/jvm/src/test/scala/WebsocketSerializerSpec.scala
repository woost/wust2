package wust.framework

import org.scalatest._
import boopickle.Default._

class WebsocketSerializerSpec extends FreeSpec with MustMatchers {
  case class Something(i: Int)

  "serialize and deserialize" in {
    val obj = Something(2)
    val serialized = WebsocketSerializer.serialize(obj)
    val deserialized = WebsocketSerializer.deserialize[Something](serialized)
    deserialized mustEqual obj
  }
}
