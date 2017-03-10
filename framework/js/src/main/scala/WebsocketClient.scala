package wust.framework

import scala.concurrent.Future
import java.nio.ByteBuffer

import boopickle.Default._

import message._

trait IncidentHandler[Event, Error] {
  def receive(event: Event): Unit
  def fromError(error: Error): Throwable
}

class WebsocketClient[Channel, Event, Error, AuthToken](
  val messages: Messages[Channel, Event, Error, AuthToken],
  handler: IncidentHandler[Event, Error]) {

  import messages._, handler._

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

  def control(control: Control): Future[Boolean] = {
    val (id, promise) = controlRequests.open()
    send(ControlRequest(id, control))
    promise.future
  }

  def call(path: Seq[String], args: Map[String, ByteBuffer]): Future[ByteBuffer] = {
    val (id, promise) = callRequests.open()
    send(CallRequest(id, path, args))
    promise.future
  }

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
