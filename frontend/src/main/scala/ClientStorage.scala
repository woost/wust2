package wust.frontend

import org.scalajs.dom.ext.Storage
import wust.ids._
import wust.api.Authentication
import wust.graph.GraphChanges
import scala.util.Try
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

class StorageOperations(storage: Storage) {
  def store(key: String, value: String): Unit = storage.update(key, value)
  def load(key: String): Option[String] = storage(key)
  def remove(key: String): Unit = storage.remove(key)
  def storeJson[T: Encoder](key: String, value: T): Unit = store(key, value.asJson.noSpaces)
  def loadJson[T: Decoder](key: String): Option[T] = load(key).flatMap(v => decode[T](v).right.toOption)
}

class ClientStorage(storage: Storage) extends StorageOperations(storage) {
  object keys {
    val token = "wust.auth.token"
    val graphChanges = "wust.graph.changes"
    val syncMode = "wust.graph.syncMode"
  }

  def token: Option[Authentication.Token] = load(keys.token)
  def token_=(auth: Option[Authentication.Token]) = auth match {
    case Some(auth) => store(keys.token, auth)
    case None => remove(keys.token)
  }

  def graphChanges: List[GraphChanges] = loadJson[List[GraphChanges]](keys.graphChanges).getOrElse(List.empty)
  def graphChanges_=(changes: List[GraphChanges]) = storeJson(keys.graphChanges, changes)

  def syncMode: Option[SyncMode] = load(keys.syncMode).flatMap(SyncMode.fromString.lift)
  def syncMode_=(mode: SyncMode) = store(keys.syncMode, mode.toString)
}
