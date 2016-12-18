package framework

import scala.concurrent.ExecutionContext.Implicits.global

import boopickle.Default._

import scalajs.js
import org.scalajs.dom._
import org.scalajs.dom.raw.MessageEvent
import collection.mutable
import concurrent.{Promise, Future}
import java.nio.ByteBuffer

import scala.scalajs.js.typedarray._
import scala.scalajs.js.typedarray.TypedArrayBufferOps._
import org.scalajs.dom.raw.{Blob, FileReader, MessageEvent, ProgressEvent}

import framework.message._

class AutowireClient(send: (Seq[String], Map[String, ByteBuffer]) => Future[ByteBuffer]) extends autowire.Client[ByteBuffer, Pickler, Pickler] {
  override def doCall(req: Request): Future[ByteBuffer] = send(req.path, req.args)
  def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
  def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)
}

trait WebsocketClient[CHANNEL, EVENT] {
  implicit def channelPickler: Pickler[CHANNEL]
  implicit def eventPickler: Pickler[EVENT]
  implicit def clientMessagePickler = ClientMessage.pickler[CHANNEL]
  implicit def serverMessagePickler = ServerMessage.pickler[EVENT]

  def receive(event: EVENT): Unit

  private val wsPromise = Promise[WebSocket]()
  private val wsFuture = wsPromise.future

  type SequenceId = Int
  private val openRequests = mutable.HashMap.empty[SequenceId, Promise[ByteBuffer]]
  private var seqId = 0
  private def nextSeqId() = {
    val r = seqId
    seqId += 1
    r
  }

  private def send(msg: ClientMessage) {
    import scala.scalajs.js.typedarray.TypedArrayBufferOps._
    val bytes = Pickle.intoBytes(msg)
    for (ws <- wsFuture) ws.send(bytes.arrayBuffer())
  }

  private def callRequest(path: Seq[String], args: Map[String, ByteBuffer]): Future[ByteBuffer] = {
    val seqId = nextSeqId()
    val call = CallRequest(seqId, path, args)
    val result = Promise[ByteBuffer]()
    openRequests += (seqId -> result)
    send(call)
    result.future
  }

  val wire = new AutowireClient(callRequest)

  def subscribe(channel: CHANNEL) = send(Subscribe(channel))

  def run(location: String) {
    val wsRaw = new WebSocket(location)
    wsRaw.onerror = { (e: ErrorEvent) =>
      console.log("error", e)
    }

    wsRaw.onopen = { (e: Event) =>
      wsPromise.success(wsRaw)
    }

    wsRaw.onmessage = { (e: MessageEvent) =>
      e.data match {
        case blob: Blob =>
          // console.log(blob)
          val reader = new FileReader()
          def onLoadEnd(ev: ProgressEvent): Any = {
            val buff = reader.result
            val msg = TypedArrayBuffer.wrap(buff.asInstanceOf[ArrayBuffer])
            val wsMsg = Unpickle[ServerMessage].fromBytes(msg)
            wsMsg match {
              case Response(seqId, result) =>
                val promise = openRequests(seqId)
                promise.success(result)
                openRequests -= seqId
              case Notification(event: EVENT) => receive(event)
            }
          }
          reader.onloadend = onLoadEnd _
          reader.readAsArrayBuffer(blob)
      }
    }
  }
}
