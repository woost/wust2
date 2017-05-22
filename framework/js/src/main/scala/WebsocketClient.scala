package wust.framework

import java.nio.ByteBuffer

import boopickle.Default._
import wust.framework.message._

import scala.concurrent.{ExecutionContext, Future}

trait IncidentHandler[Event, Error] {
  def fromError(error: Error): Throwable
  def onConnect(location: String, reconnect: Boolean): Unit
  def onEvent(event: Event): Unit
}

class WebsocketClient[Event: Pickler, Error: Pickler](handler: IncidentHandler[Event, Error])(implicit ec: ExecutionContext) {
  val messages = new Messages[Event, Error]
  import handler._
  import messages._

  private val callRequests = new OpenRequests[ByteBuffer]
  private val ws = new WebsocketConnection(onConnect _)

  private val pingIdleMillis = 115 * 1000
  private val acknowledgeTraffic: () => Unit = {
    import scala.scalajs.js.timers._
    var timeoutHandle: Option[SetTimeoutHandle] = None
    () => {
      timeoutHandle.foreach(clearTimeout)
      timeoutHandle = Option(setTimeout(pingIdleMillis)(send(Ping())))
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

  val wire = new AutowireClient(call)

  def run(location: String): Unit = ws.run(location) { bytes =>
    acknowledgeTraffic()
    Unpickle[ServerMessage].fromBytes(bytes) match {
      case CallResponse(seqId, result) => callRequests.get(seqId).foreach { req =>
        result.fold(req tryFailure fromError(_), req trySuccess _)
      }
      case Notification(event) => onEvent(event)
      case Pong() =>
    }
  }
}
