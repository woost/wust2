package wust.frontend

import monix.execution.Cancelable
import monix.reactive.OverflowStrategy.Unbounded
import monix.reactive.Observable
import boopickle.Default._
import org.scalajs.dom.ext.LocalStorage
import wust.api._
import wust.ids._
import wust.sdk._
import outwatch.Handler
import mycelium.client.IncidentHandler
import monix.execution.Scheduler.Implicits.global
import rx._
import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSGlobal("wustConfig")
object WustConfig extends js.Object {
  val wsPort: js.UndefOr[Int] = js.native
}

object ClientConfig {
  import WustConfig._
  import org.scalajs.dom.window.location

  val wsUrl = {
    val protocol = if (location.protocol == "https:") "wss" else "ws"
    //TODO: proxy with webpack devserver and only configure production port
    val port = wsPort getOrElse location.port.toInt
    s"$protocol://${location.hostname}:$port/ws"
  }
}

object Client {
  val storage = new ClientStorage(LocalStorage)

  private val eventHandler = Handler.create[Seq[ApiEvent]].unsafeRunSync()

  private val clientHandler = new IncidentHandler[ApiEvent] {
    override def onConnect(isReconnect: Boolean): Unit = {
      println(s"Connected to websocket")

      if (isReconnect) {
        storage.token.now.foreach(auth.loginToken)
      }
    }

    override def onEvents(events: Seq[ApiEvent]): Unit = eventHandler.unsafeOnNext(events)
  }

  private val ws = WustClient(ClientConfig.wsUrl, clientHandler)

  val eventObservable: Observable[Seq[ApiEvent]] = eventHandler

  val api: Api = ws.api
  val auth: AuthApi = ws.auth
}
