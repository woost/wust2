package wust.framework

import java.nio.ByteBuffer

import org.scalajs.dom._

import scala.scalajs.js.typedarray.TypedArrayBufferOps._
import scala.scalajs.js.typedarray._
import scala.util.Try

class BufferedFunction[T](f: T => Boolean) extends (T => Unit) {
  private var queue = List.empty[T]

  def apply(value: T): Unit = queue = queue :+ value
  def flush(): Unit = queue = queue.dropWhile(f)
}
object BufferedFunction {
  def apply[T](f: T => Boolean): BufferedFunction[T] = new BufferedFunction(f)
}

class JsWebsocketConnection extends WebsocketConnection {
  private var wsOpt: Option[WebSocket] = None

  private val sendMessages = BufferedFunction[ArrayBuffer] { msg =>
    wsOpt.fold(false) { ws =>
      Try(ws.send(msg)).fold(_ => false, _ => true)
    }
  }

  def send(bytes: ByteBuffer) = {
    sendMessages(bytes.arrayBuffer())
    sendMessages.flush()
  }

  def run(location: String, listener: WebsocketListener) {
    import listener._

    val websocket = new WebSocket(location)

    websocket.onerror = (e: ErrorEvent) => console.log("error", e)

    websocket.onopen = { (_: Event) =>
      wsOpt = Option(websocket)
      onConnect()
      sendMessages.flush()
    }

    websocket.onclose = { (_: Event) =>
      wsOpt = None
      onClose()
    }

    websocket.onmessage = { (e: MessageEvent) =>
      e.data match {
        case blob: Blob =>
          val reader = new FileReader()
          reader.onloadend = (_: ProgressEvent) => {
            @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
            val buff = reader.result.asInstanceOf[ArrayBuffer]
            val bytes = TypedArrayBuffer.wrap(buff)
            onMessage(bytes)
          }

          reader.readAsArrayBuffer(blob)
      }
    }
  }
}
