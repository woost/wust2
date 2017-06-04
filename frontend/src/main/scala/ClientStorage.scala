package wust.frontend

import org.scalajs.dom.ext.Storage
import wust.ids._
import wust.api.Authentication
import wust.graph.GraphChanges
import boopickle.Default._
import scala.util.Try
import java.nio.charset.Charset
import java.nio.ByteBuffer

class ClientStorage(storage: Storage) {
  object keys {
    val token = "wust.auth.token"
    val graphChanges = "wust.graph.changes"
    val syncMode = "wust.graph.syncMode"
  }

  private val charset = Charset.forName("UTF-8")

  def token: Option[Authentication.Token] = storage(keys.token)
  def token_=(auth: Option[Authentication.Token]) = auth match {
    case Some(token) => storage.update(keys.token, token)
    case None        => storage.remove(keys.token)
  }

  def graphChanges: Option[GraphChanges] = storage(keys.graphChanges).flatMap { changesStr =>
    val bytes = ByteBuffer.wrap(changesStr.getBytes(charset))
    Try(Unpickle[GraphChanges].fromBytes(bytes)).toOption
  }
  def graphChanges_=(changes: GraphChanges) = {
    val bytes = Pickle.intoBytes(changes)
    val value = charset.decode(bytes).toString
    storage.update(keys.graphChanges, value)
  }

  def syncMode: Option[SyncMode] = storage(keys.syncMode).flatMap(SyncMode.fromString.lift)
  def syncMode_=(mode: SyncMode) = {
    storage.update(keys.syncMode, mode.toString)
  }
}
