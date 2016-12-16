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

class WebsocketClient(location: String)(receive: ByteBuffer => Unit) {
  type SequenceId = Int
  private val wsRaw = new WebSocket(location)
  private val wsPromise = Promise[WebSocket]()
  private val wsFuture = wsPromise.future

  private val openRequests = mutable.HashMap.empty[SequenceId, Promise[ByteBuffer]]
  private var seqId = 0
  private def nextSeqId() = {
    val r = seqId
    seqId += 1
    r
  }

  def send(path: Seq[String], args: Map[String, ByteBuffer]): Future[ByteBuffer] = {
    val seqId = nextSeqId()
    val call = Pickle.intoBytes(CallRequest(seqId, path, args))
    val result = Promise[ByteBuffer]()
    openRequests += (seqId -> result)
    for (ws <- wsFuture) ws.send(call.toArrayBuffer)
    result.future
  }

  implicit class ByteBufferOpt(data: ByteBuffer) {
    def toArrayBuffer: ArrayBuffer = {
      if (data.hasTypedArray()) {
        // get relevant part of the underlying typed array
        data.typedArray().subarray(data.position, data.limit).buffer
      } else {
        // fall back to copying the data
        val tempBuffer = ByteBuffer.allocateDirect(data.remaining)
        val origPosition = data.position
        tempBuffer.put(data)
        data.position(origPosition)
        tempBuffer.typedArray().buffer
      }
    }
  }

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
          console.log(wsMsg.asInstanceOf[js.Any])
          wsMsg match {
            case Response(seqId, result) =>
              val promise = openRequests(seqId)
              promise.success(result)
              openRequests -= seqId
            case Notification(msg) => receive(msg)
          }
        }
        reader.onloadend = onLoadEnd _
        reader.readAsArrayBuffer(blob)
    }
  }
}

abstract class AutowireWebsocketClient[EV : Pickler](location: String) extends autowire.Client[ByteBuffer, Pickler, Pickler] {
  private val client = new WebsocketClient(location)({ msg =>
    val event = Unpickle[EV].fromBytes(msg)
    console.log("got event", event.asInstanceOf[js.Any])
    receive(event)
  })

  def receive(event: EV): Unit

  override def doCall(req: Request): Future[ByteBuffer] = client.send(req.path, req.args)
  def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
  def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)
}
