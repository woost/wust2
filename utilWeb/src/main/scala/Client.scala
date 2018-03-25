package wust.utilWeb

import covenant.http._
import java.nio.ByteBuffer
import boopickle.shapeless.Default._
import chameleon.ext.boopickle._
import wust.api._
import wust.ids._
import wust.sdk._
import wust.graph.User
import wust.util.RichFuture
import outwatch.Handler
import wust.utilWeb.outwatchHelpers._
import rx._
import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom.window
import scala.concurrent.Future
import scala.concurrent.duration._

object Client {
  import window.location
  private val isDev = location.port == "12345" // TODO better config with require
  private val wustUrl = {
    val protocol = if (location.protocol == "https:") "wss:" else "ws:"
    val hostname = location.hostname match {
      //TODO: better config for such things...
      case hostname if isDev => "localhost"
      case hostname if hostname.startsWith("m.") => location.hostname.drop(2)
      case hostname => hostname
    }
    s"$protocol//core.${hostname}:${location.port}/ws"
  }
  private val githubUrl = {
    import window.location
    s"${location.protocol}//github.${location.hostname}:${location.port}/api"
  }

  private val githubClient = HttpClient[ByteBuffer](githubUrl)
  val githubApi = githubClient.wire[PluginApi]

  val factory: WustClientFactory = WustClient(wustUrl)
  val api = factory.defaultPriority.api
  val auth = factory.defaultPriority.auth
  val push = factory.defaultPriority.push
  val observable = factory.observable
  observable.connected.foreach { _ =>
    //TODO we need to check whether the current auth.verified is still valid, otherwise better prompt the user and login with assumed auth.
    loginStorageAuth()
  }

  val storage = new ClientStorage
  def storageAuthOrAssumed = storage.auth.now getOrElse initialAssumedAuth
  private val initialAssumedAuth = Authentication.Assumed.fresh
  private def loginStorageAuth(): Unit = storageAuthOrAssumed match {
    case auth: Authentication.Assumed =>
      factory.highPriority.auth.assumeLogin(auth.user.id).log("assume login with storage id")
    case auth: Authentication.Verified =>
      factory.highPriority.auth.loginToken(auth.token).log("login with storage token")
  }
}
