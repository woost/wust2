package wust.frontend

import autowire._
import boopickle.Default._
import org.scalajs.dom.ext.LocalStorage
import wust.api._
import wust.framework._
import scala.concurrent.ExecutionContext.Implicits.global

class ApiIncidentHandler extends IncidentHandler[ApiError] {
  override def fromError(error: ApiError) = ApiException(error)
}

object Client {
  private val handler = new ApiIncidentHandler
  val ws = new WebsocketClient[ApiEvent, ApiError](handler)

  val api = ws.wire[Api]
  val auth = ws.wire[AuthApi]
  val onConnect = ws.onConnect _
  val onEvent = ws.onEvent _
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
  def authToken = currentAuth.map(_.token).orElse(storage.token)
}
