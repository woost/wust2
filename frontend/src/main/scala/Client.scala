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
import mycelium.client._
import wust.util.outwatchHelpers._
import rx._
import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom.window
import scala.concurrent.Future
import scala.concurrent.duration._

object Client {
  private val wsUrl = {
    import window.location
    val protocol = if (location.protocol == "https:") "wss" else "ws"
    s"$protocol://${location.hostname}:${location.port}/ws"
  }

  private val eventHandler = Handler.create[Seq[ApiEvent]].unsafeRunSync()
  private val clientHandler = new IncidentHandler[ApiEvent] {
    override def onConnect(): Unit = login(storageAuthOrAssumed)
    override def onEvents(events: Seq[ApiEvent]): Unit = eventHandler.unsafeOnNext(events)
  }
  private val clientFactory = JsWustClient(wsUrl, clientHandler)

  val eventObservable: Observable[Seq[ApiEvent]] = eventHandler

  def apply(sendType: SendType = SendType.WhenConnected, requestTimeout: FiniteDuration = 30 seconds) = clientFactory.sendWith(sendType, requestTimeout)
  val nowOrFail = apply(SendType.NowOrFail)
  val highPriority = apply(SendType.WhenConnected.highPriority)
  val lowPriority = apply(SendType.WhenConnected.lowPriority)
  val defaultPriority = apply(SendType.WhenConnected)
  val api: Api[Future] = defaultPriority.api
  val auth: AuthApi[Future] = defaultPriority.auth

  val storage = new ClientStorage
  def storageAuthOrAssumed: Authentication.UserProvider = storage.auth.now match {
    case Authentication.None => generateAssumedAuth()
    case auth: Authentication.UserProvider => auth
  }

  private def generateAssumedAuth() = Authentication.Assumed(User.Assumed(cuid.Cuid()))
  private def login(auth: Authentication.UserProvider): Unit = auth match {
    case auth: Authentication.Assumed =>
      highPriority.auth.assumeLogin(auth.user.id).log("assume login with storage id")
    case auth: Authentication.Verified =>
      highPriority.auth.loginToken(auth.token).log("login with storage token")
  }

  //TODO: this method is needed when we are loggedout by the server. (1) the globalstate needs to have a user and (2) we need to tell the server to assume logins (let the server do it?)
  def forceFreshAuthentication(): Authentication.UserProvider = {
    val newAuth = generateAssumedAuth()
    login(newAuth)
    newAuth
  }
}
