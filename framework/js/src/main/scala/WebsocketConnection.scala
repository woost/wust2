package wust.framework

import concurrent.{Promise, Future}
import concurrent.ExecutionContext.Implicits.global
import scalajs.js.timers.setTimeout
import scalajs.js.typedarray._, TypedArrayBufferOps._
import org.scalajs.dom._

import java.nio.ByteBuffer
import collection.mutable

class WebsocketConnection(onConnect: String => Unit) {
  private val reconnectMillis = 2000 // TODO exponential backoff
  private var wsOpt: Option[WebSocket] = None

  val messages = mutable.Queue.empty[ByteBuffer]
  private def flush(): Unit = {
    wsOpt.foreach { ws =>
      var sending = true
      while (sending && messages.nonEmpty) {
        try {
          val bytes = messages.front
          ws.send(bytes.arrayBuffer())
          messages.dequeue()
        } catch { case e: Exception => sending = false }
      }
    }
  }

  def send(bytes: ByteBuffer) {
    messages.enqueue(bytes)
    flush()
  }

  def run(location: String)(receive: ByteBuffer => Unit) {
    if (wsOpt.isDefined) return

    val wsRaw = new WebSocket(location)

    wsRaw.onerror = (e: ErrorEvent) => console.log("error", e)

    wsRaw.onopen = { (_: Event) =>
      console.log("websocket is open")
      onConnect(location)
      wsOpt = Option(wsRaw)
      flush()
    }

    wsRaw.onclose = { (_: Event) =>
      console.log(s"websocket is closed, will attempt to reconnect in ${reconnectMillis / 1000.0} seconds")
      wsOpt = None
      setTimeout(reconnectMillis)(run(location)(receive))
    }

    wsRaw.onmessage = { (e: MessageEvent) =>
      e.data match {
        case blob: Blob =>
          val reader = new FileReader()
          reader.onloadend = (ev: ProgressEvent) => {
            @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
            val buff = reader.result.asInstanceOf[ArrayBuffer]
            val bytes = TypedArrayBuffer.wrap(buff)
            receive(bytes)
          }

          reader.readAsArrayBuffer(blob)
      }
    }
  }
}
