package wust.frontend

import autowire._
import boopickle.Default._
import org.scalajs.dom.ext.LocalStorage
import wust.api._
import wust.ids._
import wust.framework._
import scala.concurrent.ExecutionContext.Implicits.global

trait ApiIncidentHandler extends IncidentHandler[ApiEvent, ApiError] {
  override def fromError(error: ApiError): Throwable = ApiException(error)
}

object Client {
  val ws = new WebsocketClient[ApiEvent, ApiError](new ReconnectingWebsocketConnection(new JsWebsocketConnection()))
  val api = ws.wire[Api]
  val auth = ws.wire[AuthApi]
  def run = ws.run _
}

object ClientCache {
  //TODO
  val storage = new ClientStorage(LocalStorage)

  private var _currentAuth: Option[Authentication] = None
  def currentAuth: Option[Authentication] = _currentAuth
  def currentAuth_=(auth: Option[Authentication]) {
    _currentAuth = auth
    // storage.token = auth.map(_.token)
  }

  def storedToken: Option[String] = None//storage.token
}
