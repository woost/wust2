package wust.webUtil

import java.nio.ByteBuffer

import org.scalajs.dom.window.{atob, btoa}

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array
import typings.base64DashJs._
import scala.scalajs.js.typedarray
import scala.scalajs.js.typedarray.byteArray2Int8Array

object BinaryConvertors {
  implicit class RichTypedArrayArrayBuffer(val bytes: typedarray.ArrayBuffer) extends AnyVal {
    // TODO: TypedArrayBuffer.wrap() ?
    @inline def toUint8Array = new Uint8Array(bytes)
  }
  implicit class RichArrayOfByte(val bytes: Array[Byte]) extends AnyVal {
    @inline def toUint8Array = new Uint8Array(byteArray2Int8Array(bytes))
  }
  implicit class RichJavaNioByteBuffer(val bytes: java.nio.ByteBuffer) extends AnyVal {
    @inline def toUint8Array = bytes.array.toUint8Array
  }
}
