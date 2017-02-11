package framework

import scala.concurrent.Future
import java.nio.ByteBuffer

import boopickle.Default._

import framework.message._

class AutowireClient(send: (Seq[String], Map[String, ByteBuffer]) => Future[ByteBuffer]) extends autowire.Client[ByteBuffer, Pickler, Pickler] {
  override def doCall(req: Request): Future[ByteBuffer] = send(req.path, req.args)
  def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
  def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)
}

abstract class WebsocketClient[CHANNEL: Pickler, EVENT: Pickler, ERROR: Pickler, AUTH: Pickler] {
  def receive(event: EVENT): Unit
  def fromError(error: ERROR): Throwable

  private val messages = new Messages[CHANNEL, EVENT, ERROR, AUTH]
  import messages._

  private val controlRequests = new OpenRequests[Boolean]
  private val callRequests = new OpenRequests[ByteBuffer]
  private lazy val ws = new WebsocketConnection

  private val acknowledgeTraffic: () => Unit = {
    import scala.scalajs.js.timers._
    var timeoutHandle: Option[SetTimeoutHandle] = None
    () => {
      timeoutHandle.foreach(clearTimeout)
      timeoutHandle = Some(setTimeout(50000)(send(Ping())))
    }
  }

  private def send(msg: ClientMessage): Unit = {
    acknowledgeTraffic()
    ws.send(Pickle.intoBytes(msg))
  }

  private def call(path: Seq[String], args: Map[String, ByteBuffer]): Future[ByteBuffer] = {
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

  val wire = new AutowireClient(call)

  def run(location: String): Unit = ws.run(location) { bytes =>
    acknowledgeTraffic()
    Unpickle[ServerMessage].fromBytes(bytes) match {
      case CallResponse(seqId, result) => callRequests.get(seqId).foreach { req =>
        result.fold(req tryFailure fromError(_), req trySuccess _)
      }
      case ControlResponse(seqId, success) => controlRequests.get(seqId).foreach(_ trySuccess success)
      case Notification(event) => receive(event)
      case Pong() =>
    }
  }
}
