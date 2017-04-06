package wust.frontend

import org.scalajs.dom.ext.Storage
import boopickle.Default._
import scala.util.Try

import wust.api.Authentication

class ClientStorage(storage: Storage) {
  object keys {
    val userId = "wust.auth.user"
    val expires = "wust.auth.expires"
    val token = "wust.auth.token"
  }

  private def storageLong(key: String) = storage(key).flatMap(id => Try(id.toLong).toOption)

  def userId: Option[Long] = storageLong(keys.userId)
  def expires: Option[Long] = storageLong(keys.expires)
  def token: Option[Authentication.Token] = storage(keys.token)

  val setAuth: Option[Authentication] => Unit = {
    case Some(auth: Authentication) =>
      storage.update(keys.userId, auth.user.id.toString)
      storage.update(keys.expires, auth.expires.toString)
      storage.update(keys.token, auth.token)
    case None                       =>
      storage.remove(keys.userId)
      storage.remove(keys.expires)
      storage.remove(keys.token)
  }
}
