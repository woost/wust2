package wust.frontend

import org.scalajs.dom.ext.Storage
import wust.ids._
import wust.api.Authentication
import wust.graph.GraphChanges
import scala.util.Try
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

class ClientStorage(storage: Storage) {
  object keys {
    val token = "wust.auth.token"
    val localGraphChanges = "wust.graph.changes"
    val syncMode = "wust.graph.syncMode"
  }

  def token: Option[Authentication.Token] = storage(keys.token)
  def token_=(auth: Option[Authentication.Token]) = auth match {
    case Some(token) => storage.update(keys.token, token)
    case None        => storage.remove(keys.token)
  }

  def localGraphChanges: List[GraphChanges] = storage(keys.localGraphChanges).flatMap { changesStr =>
    decode[List[GraphChanges]](changesStr).right.toOption
  }.getOrElse(List.empty)
  def localGraphChanges_=(changes: List[GraphChanges]) = {
    storage.update(keys.localGraphChanges, changes.asJson.noSpaces)
  }

  def syncMode: Option[SyncMode] = storage(keys.syncMode).flatMap(SyncMode.fromString.lift)
  def syncMode_=(mode: SyncMode) = {
    storage.update(keys.syncMode, mode.toString)
  }
}
