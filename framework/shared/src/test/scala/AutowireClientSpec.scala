package wust.framework

import java.nio.ByteBuffer

import autowire.Core.Request
import boopickle.Default._
import org.scalatest._

import scala.concurrent.Future

class AutowireClientSpec extends AsyncFreeSpec with MustMatchers {
  case class Something(i: Int)

  val defaultResponse = ByteBuffer.wrap(Array(1, 2, 3))
  val client = new AutowireClient((_,_) => Future.successful(defaultResponse))

  "write and read" in {
    val obj = Something(2)
    val serialized = client.write(obj)
    val deserialized = client.read[Something](serialized)
    deserialized mustEqual obj
  }

  "do call" in {
    val fut = client.doCall(Request(Seq.empty, Map.empty))
    fut.map(_ mustEqual defaultResponse)
  }
}
