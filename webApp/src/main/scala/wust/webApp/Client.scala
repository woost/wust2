package wust.webApp

import java.nio.ByteBuffer

import boopickle.Default._
import chameleon.ext.boopickle._
import covenant.http._
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom.window
import rx._
import wust.api._
import wust.api.serialize.Boopickle._
import wust.ids._
import wust.sdk._
import wust.webUtil.outwatchHelpers._

import scala.concurrent.Future
import scala.scalajs.LinkingInfo
import scala.util.{Failure, Success}

@SuppressWarnings(Array("org.wartremover.warts.FinalCaseClass")) //TODO
object Client {
  import window.location
  // in firefox or chrome: location.port is always set
  // in edge: location.port might be empty if not specified.
  private val hostname = location.hostname
  private val port = if (location.port.isEmpty) "" else ":" + location.port
  private val protocol = location.protocol

  private def calculateCoreAddress(withVersion: Boolean): String = {
    val socketProtocol = if (location.protocol == "https:") "wss:" else "ws:"

    if (LinkingInfo.developmentMode)
      s"$hostname$port" // allows to access the devserver without subdomain
    else {
      val subdomain = if (withVersion) s"core-${WoostConfig.value.versionString.replace(".", "-")}" else "core"
      s"$subdomain.$hostname$port"
    }
  }

  private def calculateCoreUrl(withVersion: Boolean): String = {
    val socketProtocol = if (location.protocol == "https:") "wss:" else "ws:"
    val address = calculateCoreAddress(withVersion)
    s"$socketProtocol//$address/ws"
  }

  private def calculateCoreHttpUrl(withVersion: Boolean): String = {
    val address = calculateCoreAddress(withVersion)
    s"${location.protocol}//$address"
  }

  private def calculateFilesUrl(): Option[String] = {
    if (LinkingInfo.developmentMode) None
    else Some(s"${location.protocol}//files.$hostname")
  }

  private val wustWsUrl = calculateCoreUrl(withVersion = true)
  private val wustHttpUrlVersioned = calculateCoreHttpUrl(withVersion = true)
  private val wustHttpUrlUnversioned = calculateCoreHttpUrl(withVersion = false)
  val wustFilesUrl = calculateFilesUrl()

  private def backendIsOnline(url: String): Future[Boolean] = {
    import org.scalajs.dom.raw.XMLHttpRequest

    import scala.concurrent.Promise

    val promise = Promise[Boolean]()
    val xmlHttp = new XMLHttpRequest();
    xmlHttp.onreadystatechange = { _ =>
        if (xmlHttp.readyState == 4) promise.trySuccess(xmlHttp.status == 200)
    }
    xmlHttp.open("GET", s"$url/health", true);
    xmlHttp.send(null);

    promise.future
  }

  def unversionedBackendIsOnline(): Future[Boolean] = backendIsOnline(wustHttpUrlUnversioned)
  def versionedBackendIsOnline(): Future[Boolean] = backendIsOnline(wustHttpUrlVersioned)

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

  val apiErrorSubject = PublishSubject[Unit]
  val factory: WustClientFactory[Future] = WustClient(wustWsUrl, apiErrorSubject, enableRequestLogging = DevOnly.isTrue)
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
      Client.storage.backendTimeDelta() = backendNow minusEpoch EpochMilli.localNow
    }
  }

  storage.backendTimeDelta.foreach { delta =>
    scribe.info(s"backend time delta: ${ delta }ms")
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
