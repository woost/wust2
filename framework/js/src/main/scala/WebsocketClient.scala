package wust.framework

import java.nio.ByteBuffer

import boopickle.Default._
import wust.framework.message._

import scala.concurrent.{ExecutionContext, Future}

trait IncidentHandler[Error] {
  def fromError(error: Error): Throwable
}

class WebsocketClient[Event: Pickler, Error: Pickler](handler: IncidentHandler[Error])(implicit ec: ExecutionContext) {
  val messages = new Messages[Event, Error]
  import handler._
  import messages._

  private val callRequests = new OpenRequests[ByteBuffer]
  private val ws = new WebsocketConnection(s => connectHandler.foreach(_(s)))

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

  private var connectHandler: Option[String => Any] = None
  def onConnect(handler: String => Any): Unit = connectHandler = Option(handler)
  private var eventHandler: Option[Event => Any] = None
  def onEvent(handler: Event => Any): Unit = eventHandler = Option(handler)

  val wire = new AutowireClient(call)

  def run(location: String): Unit = ws.run(location) { bytes =>
    acknowledgeTraffic()
    Unpickle[ServerMessage].fromBytes(bytes) match {
      case CallResponse(seqId, result) => callRequests.get(seqId).foreach { req =>
        result.fold(req tryFailure fromError(_), req trySuccess _)
      }
      case Notification(event) => eventHandler.foreach(_(event))
      case Pong() =>
    }
  }
}
