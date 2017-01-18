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

case object TimeoutException extends Exception
class OpenRequests[T](timeoutMillis: Int = 60000) {
  private val openRequests = mutable.HashMap.empty[SequenceId, Promise[T]]

  private val nextSeqId: () => SequenceId = {
    var seqId = 0
    () => { seqId += 1; seqId }
  }

  private def newPromise: Promise[T] = {
    val promise = Promise[T]()

    val timer = new Timer
    timer.schedule(new TimerTask {
      def run = promise tryFailure TimeoutException
    }, timeoutMillis)

    promise
  }

  def open(): (SequenceId, Promise[T]) = {
    val promise = newPromise
    val seqId = nextSeqId()
    openRequests += seqId -> promise
    promise.future onComplete (_ => openRequests -= seqId)
    seqId -> promise
  }

  def get(seqId: SequenceId): Option[Promise[T]] = openRequests.get(seqId)
}

abstract class WebsocketClient[CHANNEL: Pickler, EVENT: Pickler, ERROR: Pickler, AUTH: Pickler] {
  def receive(event: EVENT): Unit
  def fromError(error: ERROR): Throwable

  private val messages = new Messages[CHANNEL,EVENT,ERROR,AUTH]
  import messages._

  private val wsPromise = Promise[WebSocket]()
  private val controlRequests = new OpenRequests[Boolean]
  private val callRequests = new OpenRequests[ByteBuffer]

  private def send(msg: ClientMessage) {
    val bytes = Pickle.intoBytes(msg)
    for (ws <- wsPromise.future) ws.send(bytes.arrayBuffer())
  }

  private def request(path: Seq[String], args: Map[String, ByteBuffer]): Future[ByteBuffer] = {
    val (id, promise) = callRequests.open()
    send(CallRequest(id, path, args))
    promise.future
  }

  private def control(control: Control): Future[Boolean] = {
    val (id, promise) = controlRequests.open()
    send(ControlRequest(id, control))
    promise.future
  }

  def login(auth: AUTH): Future[Boolean] = control(Login(auth))
  def logout(): Future[Boolean] = control(Logout())
  def subscribe(channel: CHANNEL): Unit = send(Subscription(channel))
  val wire = new AutowireClient(request)

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
              case CallResponse(seqId, result) =>
                callRequests.get(seqId).foreach { req =>
                  result.fold(req tryFailure fromError(_), req trySuccess _)
                }
              case ControlResponse(seqId, success) =>
                controlRequests.get(seqId).foreach(_ trySuccess success)
              case Notification(event) => receive(event)
            }
          }

          reader.readAsArrayBuffer(blob)
      }
    }
  }
}
