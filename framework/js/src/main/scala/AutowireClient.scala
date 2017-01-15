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

//TODO: remove dependency to autowire
abstract class WebsocketClient[CHANNEL: Pickler, EVENT: Pickler, ERROR: Pickler, AUTH: Pickler] {
  def receive(event: EVENT): Unit

  case class BadRequestException(error: ERROR) extends Exception
  case object LoginFailureException extends Exception
  case object TimeoutException extends Exception
  case object SupersededAuthentication extends Exception

  private val messages = new Messages[CHANNEL,EVENT,ERROR,AUTH]
  import messages._

  private val wsPromise = Promise[WebSocket]()
  private val wsFuture = wsPromise.future

  //TODO: sequence numbers for auth requests
  private var authenticateRequest: Promise[Boolean] = Promise()
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

  private def timeoutPromise(promise: Promise[_]) {
    val timer = new Timer
    timer.schedule(new TimerTask {
      def run = promise tryFailure TimeoutException
    }, requestTimeoutMillis)
  }

  private def request(path: Seq[String], args: Map[String, ByteBuffer]): Future[ByteBuffer] = {
    val seqId = nextSeqId()
    val promise = Promise[ByteBuffer]()
    val future = promise.future
    openRequests += (seqId -> promise)
    future onComplete (_ => openRequests -= seqId)
    timeoutPromise(promise)
    send(CallRequest(seqId, path, args))
    future
  }

  def login(auth: AUTH): Future[Boolean] = {
    val promise = Promise[Boolean]()
    authenticateRequest tryFailure SupersededAuthentication
    authenticateRequest = promise
    timeoutPromise(promise)
    send(Login(auth))
    promise.future
  }

  def logout(): Future[Boolean] = {
    val promise = Promise[Boolean]()
    authenticateRequest tryFailure SupersededAuthentication
    authenticateRequest = promise
    timeoutPromise(promise)
    send(Logout())
    promise.future
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
                openRequests.get(seqId).foreach(_ tryFailure BadRequestException(error))
              case Notification(event) => receive(event)
              case LoggedIn() => authenticateRequest trySuccess true
              case LoggedOut() => authenticateRequest trySuccess true
              case LoginFailed() =>
                authenticateRequest tryFailure LoginFailureException
            }
          }
          reader.readAsArrayBuffer(blob)
      }
    }
  }
}
