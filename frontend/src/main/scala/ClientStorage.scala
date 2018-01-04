package wust.frontend

import cats.effect.IO
import org.scalajs.dom.ext.Storage
import outwatch.Sink
import outwatch.dom.Handler
import monix.reactive.{Observable, Observer}
import monix.reactive.OverflowStrategy.Unbounded
import monix.execution.Cancelable
import monix.execution.Ack.Continue
import monix.execution.Scheduler.Implicits.global
import wust.ids._
import wust.api.Authentication
import wust.graph.GraphChanges
import scala.util.Try
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import rx._
import wust.util.outwatchHelpers._


object StorageReader {
  def apply(storage: Storage)(key: String): Observable[Option[String]] = {
    Observable.create[Option[String]](Unbounded){observer =>
      observer.onNext(storage(key))
      Cancelable() //TODO
    }
  }
}

object StorageWriter {
  def apply(storage: Storage)(key: String): Sink[Option[String]] = {
    Sink.create[Option[String]](data => data match {
      case Some(data) => IO{storage.update(key, data); Continue}
      case None => IO{storage.remove(key); Continue}
    })
  }
}

class ClientStorage(storage: Storage)(implicit owner: Ctx.Owner) {
  object keys {
    val token = "wust.auth.token"
    val graphChanges = "wust.graph.changes"
    val syncMode = "wust.graph.syncMode"
  }

  val reader = StorageReader(storage) _
  val writer = StorageWriter(storage) _

  private def toJson[T: Encoder](value: T): String = value.asJson.noSpaces
  private def fromJson[T: Decoder](value: String): Option[T] = decode[T](value).right.toOption

  val token: Var[Option[Authentication.Token]] = {
    val obs: Observable[Option[Authentication.Token]] = reader(keys.token)
    val sink: Sink[Option[Authentication.Token]] = writer(keys.token)
    Handler(sink, obs).toVar(storage(keys.token))
  }

  val graphChanges: Handler[List[GraphChanges]] = {
    val obs: Observable[List[GraphChanges]] = {
      reader(keys.graphChanges)
      .map( _.flatMap (fromJson[List[GraphChanges]](_)).getOrElse(Nil))
    }
    val sink: Sink[List[GraphChanges]] = writer(keys.graphChanges) redirectMap {
      changes => Option(toJson(changes))
    }
    Handler(sink, obs)
  }

  val syncMode: Handler[Option[SyncMode]] = {
    val obs: Observable[Option[SyncMode]] = {
      reader(keys.syncMode)
      .map( _.flatMap (fromJson[SyncMode](_)))
    }
    val sink: Sink[Option[SyncMode]] = writer(keys.syncMode) redirectMap {
      mode => mode map (toJson(_))
    }
    Handler(sink, obs)
  }
}
