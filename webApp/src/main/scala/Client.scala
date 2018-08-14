package wust.webApp

import covenant.http._
import java.nio.ByteBuffer

import boopickle.Default._
import chameleon.ext.boopickle._
import wust.api._
import wust.ids._
import wust.sdk._
import wust.util.RichFuture
import outwatch.Handler
import wust.webApp.outwatchHelpers._
import rx._

import scala.scalajs.{LinkingInfo, js}
import scala.scalajs.js.annotation._
import org.scalajs.dom.window

import scala.util.{Success, Failure}
import scala.concurrent.Future
import scala.concurrent.duration._

object Client {
  import window.location
  // in firefox or chrome: location.port is always set
  // in edge: location.port might be empty if not specified.
  private val wustUrl = {
    val protocol = if (location.protocol == "https:") "wss:" else "ws:"
    val port = if (location.port.isEmpty) "" else ":" + location.port
    val hostname = location.hostname

    if (LinkingInfo.developmentMode)
      s"$protocol//${hostname}$port/ws" // allows to access the devserver without subdomain
    else
      s"$protocol//core.${hostname}$port/ws"
  }
  private val githubUrl = {
    import window.location
    s"${location.protocol}//github.${location.hostname}:8902/api"
  }
  private val slackUrl = {
    import window.location
    // s"${location.protocol}//slack.${location.hostname}:9103/api"
    s"${location.protocol}//slack.${location.hostname}/api"
  }
  private val gitterUrl = {
    import window.location
    s"${location.protocol}//gitter.${location.hostname}:8904/api"
  }

  private val githubClient = HttpClient[ByteBuffer](githubUrl)
  private val slackClient = HttpClient[ByteBuffer](slackUrl)
  private val gitterClient = HttpClient[ByteBuffer](gitterUrl)

  val githubApi = githubClient.wire[PluginApi]
  val gitterApi = gitterClient.wire[PluginApi]
  val slackApi = slackClient.wire[PluginApi]

  val factory: WustClientFactory = WustClient(wustUrl)
  val api = factory.defaultPriority.api
  val auth = factory.defaultPriority.auth
  val push = factory.defaultPriority.push
  val observable = factory.observable

  val storage = new ClientStorage
  def currentAuth = storage.auth.now getOrElse initialAssumedAuth
  private var initialAssumedAuth = Authentication.Assumed.fresh
  private def loginStorageAuth(auth: Authentication): Future[Boolean] = auth match {
    case auth: Authentication.Assumed  => factory.highPriority.auth.assumeLogin(auth.user)
    case auth: Authentication.Verified => factory.highPriority.auth.loginToken(auth.token)
  }
  storage.backendTimeDelta.foreach { delta =>
    println(s"backend time delta: ${ delta }ms")
    EpochMilli.delta = delta
  }


  //TODO backoff?
  private def doLoginWithRetry(auth: Option[Authentication]): Unit = loginStorageAuth(auth getOrElse initialAssumedAuth).onComplete {
    case Success(true) => ()
    case Success(false) =>
      scribe.warn("Login failed, token is not valid. Will switch to new assumed auth.")
      storage.auth() = None // forget invalid token
      // get a new initialAssumedAuth, as the old one might have already be used to become a real user.
      // TODO: is that enough?
      initialAssumedAuth = Authentication.Assumed.fresh
      doLoginWithRetry(Some(initialAssumedAuth))
    case Failure(t) =>
      scribe.warn("Login request failed, will retry", t)
      doLoginWithRetry(auth)
  }

  private def timeSync(): Unit = {
    Client.api.currentTime.foreach(backendNow => Client.storage.backendTimeDelta() = backendNow - EpochMilli.localNow)
  }

  // relogin when reconnecting or when localstorage-auth changes
  observable.connected.foreach { _ =>
    scribe.info("Websocket connected")
    timeSync()
    doLoginWithRetry(storage.auth.now)
  }
  storage.authFromOtherTab.foreach { doLoginWithRetry }
}
