package example

import scala.concurrent.ExecutionContext.Implicits.global

import diode._
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

import api._

object DiodeEvent {
  import diode.ActionType
  implicit object EventAction extends ActionType[EventType]
}
import DiodeEvent._

object WebsocketClient extends autowire.Client[ByteBuffer, Pickler, Pickler] {
  override def doCall(req: Request): Future[ByteBuffer] = {
    val seqId = nextSeqId()
    val call = Pickle.intoBytes(Call(seqId, req.path, req.args))
    val result = Promise[ByteBuffer]()
    openRequests += (seqId -> result)
    for (ws <- wsFuture) ws.send(call.toArrayBuffer)
    result.future
  }

  def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
  def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)

  type SequenceId = Int
  // val wsRaw = new WebSocket(s"ws://${window.location.host}")
  val wsRaw = new WebSocket(s"ws://localhost:8080")
  val wsPromise = Promise[WebSocket]()
  val wsFuture = wsPromise.future

  private val openRequests = mutable.HashMap.empty[SequenceId, Promise[ByteBuffer]]
  private var seqId = 0
  private def nextSeqId() = {
    val r = seqId
    seqId += 1
    r
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
          val wsMsg = Unpickle[WebsocketMessage].fromBytes(msg)
          console.log(wsMsg.asInstanceOf[js.Any])
          wsMsg match {
            case Response(seqId, result) =>
              val promise = openRequests(seqId)
              promise.success(result)
              openRequests -= seqId
            case event: EventType =>
              console.log("got event", event.asInstanceOf[js.Any])
              AppCircuit.dispatch(event)
            case m => println(m)
          }
        }
        reader.onloadend = onLoadEnd _
        reader.readAsArrayBuffer(blob)
    }
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

}
