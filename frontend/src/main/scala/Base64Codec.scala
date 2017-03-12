package wust.frontend

import java.nio.ByteBuffer
import scala.scalajs.js.Dynamic.{ global => g }
import org.scalajs.dom.window.{ atob, btoa }

object Base64Codec {
  def encode(buffer: ByteBuffer): String = {
    val s = new StringBuilder(buffer.limit)
    for (i <- 0 until buffer.limit) {
      val c = buffer.get
      s ++= g.String.fromCharCode(c & 0xFF).asInstanceOf[String]
    }

    btoa(s.result)
  }

  def decode(data: String): ByteBuffer = {
    val byteString = atob(data)
    val buffer = ByteBuffer.allocateDirect(byteString.size)
    byteString.foreach(c => buffer.put(c.toByte))
    buffer.flip()
    buffer
  }
}
