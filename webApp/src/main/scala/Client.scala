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
    s"${location.protocol}//github.${location.hostname}:${location.port}/api"
  }
  private val gitterUrl = {
    import window.location
    s"${location.protocol}//${location.hostname}:54321/api"
  }

  private val githubClient = HttpClient[ByteBuffer](githubUrl)
  private val gitterClient = HttpClient[ByteBuffer](gitterUrl)
  val githubApi = githubClient.wire[PluginApi]
  val gitterApi = gitterClient.wire[PluginApi]

  val factory: WustClientFactory = WustClient(wustUrl)
  val api = factory.defaultPriority.api
  val auth = factory.defaultPriority.auth
  val push = factory.defaultPriority.push
  val observable = factory.observable
  observable.connected.foreach { _ =>
    doLoginWithRetry()
  }

  val storage = new ClientStorage
  def currentAuth = storage.auth.now getOrElse initialAssumedAuth
  private var initialAssumedAuth = Authentication.Assumed.fresh
  private def loginStorageAuth(auth: Authentication): Future[Boolean] = auth match {
    case auth: Authentication.Assumed  => factory.highPriority.auth.assumeLogin(auth.user)
    case auth: Authentication.Verified => factory.highPriority.auth.loginToken(auth.token)
  }
  private def doLoginWithRetry(): Unit = loginStorageAuth(currentAuth).onComplete {
    case Success(true) => ()
    case Success(false) =>
      scribe.warn("Login failed, token is not valid. Will switch to new assumed auth.")
      storage.auth() = None // forget invalid token
      // get a new initialAssumedAuth, as the old one might have already be used to become a real user.
      // TODO: is that enough?
      initialAssumedAuth = Authentication.Assumed.fresh
      doLoginWithRetry()
    case Failure(t) =>
      scribe.warn("Login request failed, will retry", t)
      doLoginWithRetry()
  }
}
