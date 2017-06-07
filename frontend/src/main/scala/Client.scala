package wust.frontend

import autowire._
import boopickle.Default._
import org.scalajs.dom.ext.LocalStorage
import wust.api._
import wust.ids._
import wust.framework._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class ApiIncidentHandler extends IncidentHandler[ApiEvent, ApiError] {
  override def fromError(error: ApiError): Throwable = ApiException(error)
}

object Client {
  private val handler = new ApiIncidentHandler {
    override def onConnect(location: String, reconnect: Boolean): Unit = connectHandler.foreach(_(location, reconnect))
    override def onEvents(events: Seq[ApiEvent]): Unit = eventHandler.foreach(_(events))
  }

  val ws = new WebsocketClient[ApiEvent, ApiError](handler)

  private var connectHandler: Option[(String, Boolean) => Any] = None
  def onConnect(handler: (String, Boolean) => Any): Unit = connectHandler = Option(handler)
  private var eventHandler: Option[Seq[ApiEvent] => Any] = None
  def onEvents(handler: Seq[ApiEvent] => Any): Unit = eventHandler = Option(handler)

  val api = ws.wire[Api]
  val auth = ws.wire[AuthApi]
  val run = ws.run _

  val storage = new ClientStorage(LocalStorage)
}

object ClientCache {
  import Client.storage

  private var _currentAuth: Option[Authentication] = None
  def currentAuth: Option[Authentication] = _currentAuth
  def currentAuth_=(auth: Option[Authentication]) {
    _currentAuth = auth
    storage.token = auth.map(_.token)
  }

  def storedToken: Option[String] = storage.token
}
