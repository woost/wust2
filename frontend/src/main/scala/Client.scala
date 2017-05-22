package wust.frontend

import autowire._
import boopickle.Default._
import org.scalajs.dom.ext.LocalStorage
import wust.api._
import wust.framework._
import scala.concurrent.ExecutionContext.Implicits.global

class ApiIncidentHandler(connectHandler: (String, Boolean) => Any, eventHandler: ApiEvent => Any) extends IncidentHandler[ApiEvent, ApiError] {
  override def fromError(error: ApiError): Throwable = ApiException(error)
  override def onConnect(location: String, reconnect: Boolean): Unit = connectHandler(location, reconnect)
  override def onEvent(event: ApiEvent): Unit = eventHandler(event)
}

object Client {
  private val handler = new ApiIncidentHandler((l,r) => connectHandler.foreach(_(l,r)), e => eventHandler.foreach(_(e)))
  val ws = new WebsocketClient[ApiEvent, ApiError](handler)

  private var connectHandler: Option[(String, Boolean) => Any] = None
  def onConnect(handler: (String, Boolean) => Any): Unit = connectHandler = Option(handler)
  private var eventHandler: Option[ApiEvent => Any] = None
  def onEvent(handler: ApiEvent => Any): Unit = eventHandler = Option(handler)

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
