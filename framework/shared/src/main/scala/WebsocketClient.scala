package wust.framework

import java.nio.ByteBuffer
import java.util.{Timer, TimerTask}

import boopickle.Default._
import wust.framework.message._

import scala.concurrent.{ExecutionContext, Future}

trait IncidentHandler[Event, Failure] {
  def fromError(error: Failure): Throwable
  def onConnect(reconnect: Boolean): Unit
  def onEvents(events: Seq[Event]): Unit
}

class WebsocketClient[Event: Pickler, Failure: Pickler](ws: WebsocketConnection)(implicit ec: ExecutionContext) {
  private val messages = new Messages[Event, Failure]
  import messages._

  private val callRequests = new OpenRequests[ByteBuffer]

  private val timer = new Timer
  private val pingIdleMillis = 115 * 1000
  private val acknowledgeTraffic: () => Unit = {
    var currTask = Option.empty[TimerTask]
    () => {
      currTask.foreach(_.cancel())
      timer.purge()
      val task = new TimerTask { def run() = send(Ping()) }
      timer.schedule(task, pingIdleMillis)
      currTask = Some(task)
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

  def run(location: String, handler: IncidentHandler[Event, Failure]): Unit = ws.run(location, new WebsocketListener {
    private var wasClosed = false
    def onConnect() = handler.onConnect(wasClosed)
    def onClose() = wasClosed = true
    def onMessage(bytes: ByteBuffer): Unit = {
      acknowledgeTraffic()
      Unpickle[ServerMessage].fromBytes(bytes) match {
        case CallResponse(seqId, result) => callRequests.get(seqId).foreach { req =>
          result.fold(req tryFailure handler.fromError(_), req trySuccess _)
        }
        case Notification(events) => handler.onEvents(events)
        case Pong()               =>
      }
    }
  })

  def stop(): Unit = {
    timer.cancel()
    //TODO: stop ws?
  }
}
