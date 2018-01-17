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
import outwatch.util.LocalStorage

class ClientStorage(storage: Storage)(implicit owner: Ctx.Owner) {
  object keys {
    val token = "wust.auth.token"
    val graphChanges = "wust.graph.changes"
    val syncMode = "wust.graph.syncMode"
  }

  private def toJson[T: Encoder](value: T): String = value.asJson.noSpaces
  private def fromJson[T: Decoder](value: String): Option[T] = decode[T](value).right.toOption

  val token: Var[Option[Authentication.Token]] = {
    LocalStorage
      .handler(keys.token).unsafeRunSync()
      .toVar(storage(keys.token))
  }

  val graphChanges: Handler[List[GraphChanges]] = {
    LocalStorage.handler(keys.graphChanges).unsafeRunSync()
      .imap(_.flatMap(fromJson[List[GraphChanges]]).getOrElse(Nil))(changes => Option(toJson(changes)))
  }

  val syncMode: Handler[Option[SyncMode]] = {
    LocalStorage.handler(keys.syncMode).unsafeRunSync()
      .imap(_.flatMap(fromJson[SyncMode]))(mode => mode.map(toJson(_)))
  }
}
