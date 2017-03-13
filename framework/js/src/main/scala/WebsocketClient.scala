package wust.framework

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.ByteBuffer

import boopickle.Default._

import message._

trait IncidentHandler[Event, Error] {
  def fromError(error: Error): Throwable
  def onConnect(location: String): Unit
  def receive(event: Event): Unit
}

class WebsocketClient[Channel: Pickler, Event: Pickler, Error: Pickler, AuthToken: Pickler](
    handler: IncidentHandler[Event, Error]) {
  val messages = new Messages[Channel, Event, Error, AuthToken]

  import messages._, handler._

  private val controlRequests = new OpenRequests[Boolean]
  private val callRequests = new OpenRequests[ByteBuffer]
  private val ws = new WebsocketConnection(onConnect)

  private val pingIdleMillis = 115 * 1000
  private val acknowledgeTraffic: () => Unit = {
    import scala.scalajs.js.timers._
    var timeoutHandle: Option[SetTimeoutHandle] = None
    () => {
      timeoutHandle.foreach(clearTimeout)
      timeoutHandle = Some(setTimeout(pingIdleMillis)(send(Ping())))
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

  def login(auth: AuthToken): Future[Boolean] = control(Login(auth))
  def logout(): Future[Boolean] = control(Logout())
  def subscribe(channel: Channel): Future[Boolean] = control(Subscribe(channel))
  def unsubscribe(channel: Channel): Future[Boolean] = control(Unsubscribe(channel))

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
