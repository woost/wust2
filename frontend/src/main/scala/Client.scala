package wust.frontend

import autowire._
import boopickle.Default._
import org.scalajs.dom.ext.LocalStorage
import wust.api._
import wust.framework._
import scala.concurrent.ExecutionContext.Implicits.global

class ApiIncidentHandler(onConnect: (String, Boolean) => Any, onEvent: Event => Any) extends IncidentHandler[ApiEvent, ApiError] {
  override def fromError(error: ApiError) = ApiException(error)
  override def onConnect(location: String, reconnect: Boolean) = onConnect(location, reconnect)
  override def onEvent(event: ApiEvent) = onEvent(event)
}

object Client {
  private val handler = new ApiIncidentHandler((l,r) => connectHandler.foreach(_(l,r)), e => eventHandler(_(e)))
  val ws = new WebsocketClient[ApiEvent, ApiError](handler)

  private var connectHandler: Option[(String, Boolean) => Any] = None
  def onConnect(handler: (String, Boolean) => Any): Unit = connectHandler = Option(handler)
  private var eventHandler: Option[Event => Any] = None
  def onEvent(handler: Event => Any): Unit = eventHandler = Option(handler)

  val api = ws.wire[Api]
  val auth = ws.wire[AuthApi]
  val run = ws.run _
}

object ClientCache {
  private val storage = new ClientStorage(LocalStorage)

  private var _currentAuth: Option[Authentication] = None
  def currentAuth: Option[Authentication] = _currentAuth
  def currentAuth_=(auth: Option[Authentication]) {
    _currentAuth = auth
    storage.token = auth.map(_.token)
  }

  def storedToken: Option[String] = storage.token
}
