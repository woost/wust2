package wust.frontend

import org.scalajs.dom.ext.Storage
import wust.api.Authentication

class ClientStorage(storage: Storage) {
  object keys {
    val token = "wust.auth.token"
  }

  def token: Option[Authentication.Token] = storage(keys.token)
  def token_=(auth : Option[Authentication.Token]) = auth match {
    case Some(token) => storage.update(keys.token, token)
    case None => storage.remove(keys.token)
  }
}
