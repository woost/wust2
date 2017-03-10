package wust.framework

import scala.concurrent.{Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.scalajs.js.typedarray._, TypedArrayBufferOps._
import org.scalajs.dom._

import java.nio.ByteBuffer

class WebsocketConnection {
  private val wsPromise = Promise[WebSocket]()

  def send(bytes: ByteBuffer) {
    for (ws <- wsPromise.future) ws.send(bytes.arrayBuffer())
  }

  def run(location: String)(receive: ByteBuffer => Unit) {
    val wsRaw = new WebSocket(location)

    wsRaw.onerror = (e: ErrorEvent) => console.log("error", e)
    wsRaw.onopen = (_: Event) => wsPromise success wsRaw
    wsRaw.onmessage = { (e: MessageEvent) =>
      e.data match {
        case blob: Blob =>
          val reader = new FileReader()
          reader.onloadend = (ev: ProgressEvent) => {
            val buff = reader.result.asInstanceOf[ArrayBuffer]
            val bytes = TypedArrayBuffer.wrap(buff)
            receive(bytes)
          }

          reader.readAsArrayBuffer(blob)
      }
    }
  }
}
