package wust.frontend

import covenant.http._
import java.nio.ByteBuffer
import boopickle.Default._
import chameleon.ext.boopickle._
import wust.api._
import wust.ids._
import wust.sdk._
import wust.graph.User
import wust.util.RichFuture
import outwatch.Handler
import wust.util.outwatchHelpers._
import rx._
import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom.window
import scala.concurrent.Future
import scala.concurrent.duration._

object Client extends WustClientOps {
  private val wustUrl = {
    import window.location
    val protocol = if (location.protocol == "https:") "wss:" else "ws:"
    s"$protocol//${location.hostname}:${location.port}/ws"
  }
  private val githubUrl = {
    import window.location
    //TODO fix url: port/path/subdomain?
    s"${location.protocol}//${location.hostname}:54321/api"
  }

  private val clientHandler = new WustIncidentHandler {
    override def onConnect(): Unit = {
      //TODO we need to check whether the current auth.verified is still valid, otherwise better prompt the user and login with assumed auth.
      loginStorageAuth()
    }
  }

  private val githubClient = HttpClient[ByteBuffer](githubUrl)
  val githubApi = githubClient.wire[PluginApi]

  val clientFactory = JsWustClient(wustUrl, clientHandler)
  val eventObservable = clientHandler.eventObservable

  val storage = new ClientStorage
  def storageAuthOrAssumed = storage.auth.now getOrElse initialAssumedAuth
  private val initialAssumedAuth = Authentication.Assumed.fresh
  private def loginStorageAuth(): Unit = storageAuthOrAssumed match {
    case auth: Authentication.Assumed =>
      highPriority.auth.assumeLogin(auth.user.id).log("assume login with storage id")
    case auth: Authentication.Verified =>
      highPriority.auth.loginToken(auth.token).log("login with storage token")
  }
}
