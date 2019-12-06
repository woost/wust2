package wust.webApp.jsdom

import java.nio.ByteBuffer

import org.scalajs.dom.window.{atob, btoa}

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array
import typings.base64DashJs._
import scala.scalajs.js.typedarray
import scala.scalajs.js.typedarray.byteArray2Int8Array

object Base64Codec {
  import js.Dynamic.{global => g}

  def encode(bytes: Uint8Array):String = base64DashJsMod.fromByteArray(bytes.asInstanceOf[typings.std.Uint8Array])

  def decode(data: String): ByteBuffer = {
    // remove urlsafety first:
    val base64Data = data.replace("_", "/").replace("-", "+")

    val byteString = atob(base64Data)
    val buffer = ByteBuffer.allocateDirect(byteString.size)
    byteString.foreach(c => buffer.put(c.toByte))
    buffer.flip()
    buffer
  }
}
