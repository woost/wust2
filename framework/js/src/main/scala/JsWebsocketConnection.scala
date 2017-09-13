package wust.framework

import java.nio.ByteBuffer

import org.scalajs.dom._
import scala.scalajs.js.typedarray.TypedArrayBufferOps._
import scala.scalajs.js.typedarray._

class JsWebsocketConnection extends WebsocketConnection {
  private var wsOpt: Option[WebSocket] = None

  def send(bytes: ByteBuffer) = wsOpt.foreach { ws =>
    ws.send(bytes.arrayBuffer())
  }

  def run(location: String, listener: WebsocketListener) {
    if (wsOpt.isDefined) return
    import listener._

    val websocket = new WebSocket(location)

    websocket.onerror = (e: ErrorEvent) => console.log("error", e)

    websocket.onopen = { (_: Event) =>
      wsOpt = Option(websocket)
      onConnect()
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
