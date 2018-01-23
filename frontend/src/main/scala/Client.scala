package wust.frontend

import monix.execution.Cancelable
import monix.reactive.OverflowStrategy.Unbounded
import monix.reactive.Observable
import boopickle.Default._
import wust.api._
import wust.ids._
import wust.sdk._
import wust.graph.User
import wust.util.RichFuture
import outwatch.Handler
import mycelium.client.IncidentHandler
import wust.util.outwatchHelpers._
import rx._
import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom.window
import scala.concurrent.Future

object Client {
  val storage = new ClientStorage

  private val wsUrl = {
    import window.location
    val protocol = if (location.protocol == "https:") "wss" else "ws"
    s"$protocol://${location.hostname}:${location.port}/ws"
  }

  private val eventHandler = Handler.create[Seq[ApiEvent]].unsafeRunSync()

  private val clientHandler = new IncidentHandler[ApiEvent] {
    override def onConnect(isReconnect: Boolean): Unit = {
      println(s"Connected to websocket")
      if (isReconnect) ensureLogin()
    }

    override def onEvents(events: Seq[ApiEvent]): Unit = eventHandler.unsafeOnNext(events)
  }

  private val ws = WustClient(wsUrl, clientHandler)

  val eventObservable: Observable[Seq[ApiEvent]] = eventHandler

  val api: Api[Future] = ws.api
  val auth: AuthApi[Future] = ws.auth

  def ensureLogin(): Authentication.UserProvider = storage.auth.now match {
    case Authentication.None =>
      val userId = cuid.Cuid()
      Client.auth.assumeLogin(userId).log("assume login with new id")
      Authentication.Assumed(User.Assumed(userId))
    case auth: Authentication.Assumed =>
      Client.auth.assumeLogin(auth.user.id).log("assume login with storage id")
      auth
    case auth: Authentication.Verified =>
      Client.auth.loginToken(auth.token).log("login with storage token")
      auth
  }
}
