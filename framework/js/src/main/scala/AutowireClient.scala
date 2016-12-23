package framework

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

import boopickle.Default._

import scalajs.js
import org.scalajs.dom._
import collection.mutable
import concurrent.{Promise, Future}
import java.nio.ByteBuffer
import java.util.{Timer,TimerTask}

import scala.scalajs.js.typedarray._
  import scala.scalajs.js.typedarray.TypedArrayBufferOps._
import org.scalajs.dom.raw.{Blob, FileReader, MessageEvent, ProgressEvent}

import framework.message._, Messages._

class AutowireClient(send: (Seq[String], Map[String, ByteBuffer]) => Future[ByteBuffer]) extends autowire.Client[ByteBuffer, Pickler, Pickler] {
  override def doCall(req: Request): Future[ByteBuffer] = send(req.path, req.args)
  def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
  def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)
}

case class BadRequestException(error: String) extends Exception(error)
case object TimeoutException extends Exception

abstract class WebsocketClient[CHANNEL: Pickler, EVENT: Pickler] {
  def receive(event: EVENT): Unit

  private val messages = new Messages[CHANNEL,EVENT]
  import messages._

  private val wsPromise = Promise[WebSocket]()
  private val wsFuture = wsPromise.future

  private val openRequests = mutable.HashMap.empty[SequenceId, Promise[ByteBuffer]]
  private val requestTimeoutMillis = 10000
  private var seqId = 0
  private def nextSeqId() = {
    val r = seqId
    seqId += 1
    r
  }

  private def send(msg: ClientMessage) {
    val bytes = Pickle.intoBytes(msg)
    for (ws <- wsFuture) ws.send(bytes.arrayBuffer())
  }

  private def request(path: Seq[String], args: Map[String, ByteBuffer]): Future[ByteBuffer] = {
    val seqId = nextSeqId()
    val call = CallRequest(seqId, path, args)
    val promise = Promise[ByteBuffer]()
    val future = promise.future
    openRequests += (seqId -> promise)
    future onComplete (_ => openRequests -= seqId)

    val timer = new Timer
    timer.schedule(new TimerTask {
      def run = promise tryFailure TimeoutException
    }, requestTimeoutMillis)

    send(call)
    future
  }

  val wire = new AutowireClient(request)

  def subscribe(channel: CHANNEL) = send(Subscription(channel))

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
          val reader = new FileReader()
          reader.onloadend = (ev : ProgressEvent) => {
            val buff = reader.result.asInstanceOf[ArrayBuffer]
            val wrapped = TypedArrayBuffer.wrap(buff)
            val wsMsg = Unpickle[ServerMessage].fromBytes(wrapped)
            wsMsg match {
              case Response(seqId, result) =>
                openRequests.get(seqId).foreach(_ trySuccess result)
              case BadRequest(seqId, error) =>
                openRequests.get(seqId).foreach(_ tryFailure new BadRequestException(error))
              case Notification(event) => receive(event)
            }
          }
          reader.readAsArrayBuffer(blob)
      }
    }
  }
}
