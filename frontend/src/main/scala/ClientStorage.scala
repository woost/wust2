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
    val graphChanges = "wust.graph.changes"
    val syncMode = "wust.graph.syncMode"
  }

  def token: Option[Authentication.Token] = storage(keys.token)
  def token_=(auth: Option[Authentication.Token]) = auth match {
    case Some(token) => storage.update(keys.token, token)
    case None        => storage.remove(keys.token)
  }

  def graphChanges: Option[GraphChanges] = storage(keys.graphChanges).flatMap { changesStr =>
    decode[GraphChanges](changesStr).right.toOption
  }
  def graphChanges_=(changes: GraphChanges) = {
    storage.update(keys.graphChanges, changes.asJson.noSpaces)
  }

  def syncMode: Option[SyncMode] = storage(keys.syncMode).flatMap(SyncMode.fromString.lift)
  def syncMode_=(mode: SyncMode) = {
    storage.update(keys.syncMode, mode.toString)
  }
}
