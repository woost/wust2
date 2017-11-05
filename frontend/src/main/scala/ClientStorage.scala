package wust.frontend

import cats.effect.IO
import org.scalajs.dom.ext.Storage
import outwatch.Sink
import outwatch.dom.Handler
import rxscalajs.Observable
import wust.ids._
import wust.api.Authentication
import wust.graph.GraphChanges
import scala.util.Try
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

  // def store(key: String, value: String): Unit = storage.update(key, value)
  // def load(key: String): Option[String] = storage(key)
  // def remove(key: String): Unit = storage.remove(key)
object StorageReader {
  def apply(storage: Storage)(key: String): Observable[Option[String]] = {
    Observable.create[Option[String]](observer => observer.next(storage(key)))
  }
}

object StorageWriter {
  def apply(storage: Storage)(key: String): Sink[Option[String]] = {
    Sink.create[Option[String]](data => data match {
      case Some(data) => IO(storage.update(key, data))
      case None => IO(storage.remove(key))
    })
  }
}

class ClientStorage(storage: Storage) {
  object keys {
    val token = "wust.auth.token"
    val graphChanges = "wust.graph.changes"
    val syncMode = "wust.graph.syncMode"
  }

  val reader = StorageReader(storage) _
  val writer = StorageWriter(storage) _

  // def storeJson[T: Encoder](key: String, value: T): Unit = store(key, value.asJson.noSpaces)
  // def loadJson[T: Decoder](key: String): Option[T] = load(key).flatMap(v => decode[T](v).right.toOption)
  private def toJson[T: Encoder](value: T): String = value.asJson.noSpaces
  private def fromJson[T: Decoder](value: String): Option[T] = decode[T](value).right.toOption

  val token: Handler[Option[Authentication.Token]] = {
    val obs: Observable[Option[Authentication.Token]] = reader(keys.token)
    val sink: Sink[Option[Authentication.Token]] = writer(keys.token)
    Sink.ObservableSink(sink, obs)
  }

  val graphChanges: Handler[List[GraphChanges]] = {
    val obs: Observable[List[GraphChanges]] = {
      reader(keys.graphChanges)
      .map( _.flatMap (fromJson[List[GraphChanges]](_)).getOrElse(Nil))
    }
    val sink: Sink[List[GraphChanges]] = writer(keys.graphChanges) redirectMap {
      changes => Option(toJson(changes))
    }
    Sink.ObservableSink(sink, obs)
  }

  val syncMode: Handler[Option[SyncMode]] = {
    val obs: Observable[Option[SyncMode]] = {
      reader(keys.syncMode)
      .map( _.flatMap (fromJson[SyncMode](_)))
    }
    val sink: Sink[Option[SyncMode]] = writer(keys.syncMode) redirectMap {
      mode => mode map (toJson(_))
    }
    Sink.ObservableSink(sink, obs)
  }
}
  // def syncMode: Option[SyncMode] = load(keys.syncMode).flatMap(SyncMode.fromString.lift)
  // def syncMode_=(mode: SyncMode) = store(keys.syncMode, mode.toString)
