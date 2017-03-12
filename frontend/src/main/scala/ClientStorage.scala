package wust.frontend

import org.scalajs.dom.ext.Storage
import boopickle.Default._

import wust.api.Authentication
import wust.util.Pipe

class ClientStorage(storage: Storage) {
  private val authKey = "wust.auth"

  def getAuth: Option[Authentication] =
    storage(authKey).map(_ |> Base64Codec.decode |> Unpickle[Authentication].fromBytes)

  val setAuth: Option[Authentication] => Unit = {
    case Some(auth: Authentication) => Pickle.intoBytes(auth) |> Base64Codec.encode |> (storage.update(authKey, _))
    case None                       => storage.remove(authKey)
  }
}
