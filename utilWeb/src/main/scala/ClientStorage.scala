package wust.utilWeb

import wust.sdk.SyncMode
import cats.effect.IO
import org.scalajs.dom.ext.Storage
import outwatch.Sink
import outwatch.dom.Handler
import monix.reactive.{Observable, Observer}
import monix.reactive.OverflowStrategy.Unbounded
import monix.execution.Cancelable
import monix.execution.Ack.Continue
import wust.ids._
import wust.api.Authentication
import wust.api.serialize.Circe._
import wust.graph.{User, GraphChanges}
import scala.util.Try
import io.circe._, io.circe.syntax._, io.circe.generic.semiauto._, io.circe.parser._
import rx._
import wust.utilWeb.outwatchHelpers._
import outwatch.util.LocalStorage //TODO use outwatch.util.Storage(dom.Storage)

class ClientStorage(implicit owner: Ctx.Owner) {
  private val internal = org.scalajs.dom.ext.LocalStorage

  implicit val SyncModeDecoder: Decoder[SyncMode] = deriveDecoder[SyncMode]
  implicit val SyncModeEncoder: Encoder[SyncMode] = deriveEncoder[SyncMode]

  object keys {
    val auth = "wust.auth"
    val graphChanges = "wust.graph.changes"
    val syncMode = "wust.graph.syncMode"
  }

  private def toJson[T: Encoder](value: T): String = value.asJson.noSpaces
  private def fromJson[T: Decoder](value: String): Option[T] = decode[T](value).right.toOption

  //TODO: howto handle with events from other tabs?
  val auth: Var[Option[Authentication]] = {
    LocalStorage.handlerWithoutEvents(keys.auth).unsafeRunSync()
      .imap(_.flatMap(fromJson[Authentication]))(auth => Option(toJson(auth)))
      .toVar(internal(keys.auth).flatMap(fromJson[Authentication]))
  }

  //TODO: howto handle with events from other tabs?
  val graphChanges: Handler[List[GraphChanges]] = {
    LocalStorage.handlerWithoutEvents(keys.graphChanges).unsafeRunSync()
      .imap(_.flatMap(fromJson[List[GraphChanges]]).getOrElse(Nil))(changes => Option(toJson(changes)))
  }

  val syncMode: Var[Option[SyncMode]] = {
    LocalStorage.handler(keys.syncMode).unsafeRunSync()
      .imap(_.flatMap(fromJson[SyncMode]))(mode => mode.map(toJson(_)))
      .toVar(internal(keys.syncMode).flatMap(fromJson[SyncMode]))
  }
}
