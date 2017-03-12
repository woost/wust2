package wust.framework

import concurrent.{Promise, Future}
import concurrent.ExecutionContext.Implicits.global
import scalajs.js.timers.setTimeout
import scalajs.js.typedarray._, TypedArrayBufferOps._
import org.scalajs.dom._

import java.nio.ByteBuffer

class WebsocketConnection(onConnect: String => Unit) {
  private val reconnectMillis = 2000 // TODO exponential backoff
  private var wsPromise = Promise[WebSocket]()
  private var initialized = false

  def send(bytes: ByteBuffer) {
    for (ws <- wsPromise.future) ws.send(bytes.arrayBuffer())
  }

  def run(location: String)(receive: ByteBuffer => Unit) {
    if (initialized) return
    initialized = true

    val wsRaw = new WebSocket(location)
    wsRaw.onerror = (e: ErrorEvent) => console.log("error", e)
    wsRaw.onopen = { (_: Event) =>
      console.log("websocket is open")
      onConnect(location)
      wsPromise success wsRaw
    }
    wsRaw.onclose = { (_: Event) =>
      console.log(s"websocket is closed, will attempt to reconnect in ${reconnectMillis / 1000.0} seconds")
      wsPromise = Promise[WebSocket]()
      initialized = false
      setTimeout(reconnectMillis)(run(location)(receive))
    }
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
