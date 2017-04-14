package wust.framework

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.ByteBuffer

import boopickle.Default._

import message._

trait IncidentHandler[Error] {
  def fromError(error: Error): Throwable
}

class WebsocketClient[Channel: Pickler, Event: Pickler, Error: Pickler, Token: Pickler](
  handler: IncidentHandler[Error]
) {
  val messages = new Messages[Channel, Event, Error, Token]

  import messages._, handler._

  private val controlRequests = new OpenRequests[Boolean]
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

  private def control(control: Control): Future[Boolean] = {
    val (id, promise) = controlRequests.open()
    send(ControlRequest(id, control))
    promise.future
  }

  private var connectHandler: Option[String => Any] = None
  def onConnect(handler: String => Any): Unit = connectHandler = Option(handler)
  private var eventHandler: Option[Event => Any] = None
  def onEvent(handler: Event => Any): Unit = eventHandler = Option(handler)

  def login(token: Token): Future[Boolean] = control(Login(token))
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
      case Notification(event) => eventHandler.foreach(_(event))
      case Pong() =>
    }
  }
}
