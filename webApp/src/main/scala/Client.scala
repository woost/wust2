package wust.webApp

import java.nio.ByteBuffer

import boopickle.Default._
import chameleon.ext.boopickle._
import covenant.http._
import org.scalajs.dom.window
import rx._
import wust.api._
import wust.api.serialize.Boopickle._
import wust.ids._
import wust.sdk._
import wust.webApp.outwatchHelpers._

import scala.concurrent.Future
import scala.scalajs.LinkingInfo
import scala.util.{Failure, Success}

object Client {
  import window.location
  // in firefox or chrome: location.port is always set
  // in edge: location.port might be empty if not specified.
  private val hostname = location.hostname
  private val port = if (location.port.isEmpty) "" else ":" + location.port
  private val protocol = location.protocol

  private val wustUrl = {
    val socketProtocol = if (location.protocol == "https:") "wss:" else "ws:"

    if (LinkingInfo.developmentMode)
      s"$socketProtocol//${hostname}$port/ws" // allows to access the devserver without subdomain
    else
      s"$socketProtocol//core.${hostname}$port/ws"
  }

  private val githubUrl = {
    if (LinkingInfo.developmentMode)
      s"$protocol//${hostname}:9101/api" // allows to access the devserver without subdomain
    else
      s"$protocol//github.${hostname}/api"
  }

  private val gitterUrl = {
    if (LinkingInfo.developmentMode)
      s"$protocol//${hostname}:9102/api" // allows to access the devserver without subdomain
    else
      s"$protocol//gitter.${hostname}/api"
  }

  private val slackUrl = {
    if (LinkingInfo.developmentMode)
      s"$protocol//${hostname}:9103/api" // allows to access the devserver without subdomain
    else
      s"$protocol//slack.${hostname}/api"
  }

  private val githubClient = HttpClient[ByteBuffer](githubUrl)
  private val slackClient = HttpClient[ByteBuffer](slackUrl)
  private val gitterClient = HttpClient[ByteBuffer](gitterUrl)

  val githubApi = githubClient.wire[PluginApi]
  val gitterApi = gitterClient.wire[PluginApi]
  val slackApi = slackClient.wire[PluginApi]

  val factory: WustClientFactory[Future] = WustClient(wustUrl)
  val api = factory.defaultPriority.api
  val auth = factory.defaultPriority.auth
  val push = factory.defaultPriority.push
  val observable = factory.observable

  val storage = new ClientStorage
  def currentAuth = storage.auth.now getOrElse initialAssumedAuth
  private var initialAssumedAuth = Authentication.Assumed.fresh
  private var lastSuccessAuth: Option[Authentication] = None
  private def loginStorageAuth(auth: Authentication): Future[Boolean] = auth match {
    case auth: Authentication.Assumed  => factory.highPriority.auth.assumeLogin(auth.user)
    case auth: Authentication.Verified => factory.highPriority.auth.loginToken(auth.token)
  }

  //TODO backoff?
  private def doLoginWithRetry(auth: Option[Authentication]): Unit = {
    val nextAuth = auth getOrElse initialAssumedAuth
    loginStorageAuth(auth getOrElse initialAssumedAuth).onComplete {
      case Success(true) =>
        lastSuccessAuth = Some(nextAuth)
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
  }

  private def timeSync(): Unit = {
    Client.api.currentTime.foreach { backendNow =>
      Client.storage.backendTimeDelta() = backendNow - EpochMilli.localNow
    }
  }

  storage.backendTimeDelta.foreach { delta =>
    println(s"backend time delta: ${ delta }ms")
    EpochMilli.delta = delta
  }

  // relogin when reconnecting
  observable.connected.foreach { _ =>
    scribe.info("Websocket connected")
    timeSync()
    doLoginWithRetry(storage.auth.now)
  }

  // new login if auth changed in other tab. ignore the initial events which comes directly.
  // storage.auth.triggerLater { auth =>
  //   if (auth != lastSuccessAuth) {
  //     scribe.info(s"Authentication changed in other tab: $auth")
  //     doLoginWithRetry(auth)
  //   }
  // }
}
