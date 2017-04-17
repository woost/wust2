package wust.frontend

import org.scalajs.dom.ext.Storage
import boopickle.Default._
import scala.util.Try

import wust.api.Authentication

class ClientStorage(storage: Storage) {
  object keys {
    val userId = "wust.auth.user"
    val token = "wust.auth.token"
  }

  private def storageLong(key: String) = storage(key).flatMap(id => Try(id.toLong).toOption)

  def userId: Option[Long] = storageLong(keys.userId)
  def token: Option[Authentication.Token] = storage(keys.token)
  val setAuth: Option[Authentication] => Unit = {
    case Some(Authentication(user, token)) =>
      storage.update(keys.userId, user.id.toString)
      storage.update(keys.token, token)
    case None                       =>
      storage.remove(keys.userId)
      storage.remove(keys.token)
  }
}
