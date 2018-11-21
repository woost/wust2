package wust.webApp

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import outwatch.dom._
import outwatch.util.LocalStorage
import rx._
import wust.api.Authentication
import wust.api.serialize.Circe._
import wust.graph.GraphChanges
import wust.webApp.BrowserDetect
import wust.webApp.outwatchHelpers._ //TODO use outwatch.util.Storage(dom.Storage)

class ClientStorage(implicit owner: Ctx.Owner) {
  private val internal = org.scalajs.dom.ext.LocalStorage

  object keys {
    val auth = "wust.auth"
    val sidebarOpen = "wust.sidebar.open"
    val graphChanges = "wust.graph.changes"
    val backendTimeDelta = "wust.backendtimedelta"
  }

  private def toJson[T: Encoder](value: T): String = value.asJson.noSpaces
  private def fromJson[T: Decoder](value: String): Option[T] = decode[T](value).right.toOption

  val auth: Var[Option[Authentication]] = {
    LocalStorage
      .handlerWithoutEvents(keys.auth)
      .unsafeRunSync()
      .mapHandler[Option[Authentication]](auth => Option(toJson(auth)))(_.flatMap(fromJson[Authentication]))
      .unsafeToVar(internal(keys.auth).flatMap(fromJson[Authentication]))
  }

  //TODO: howto handle with events from other tabs?
  val graphChanges: Handler[List[GraphChanges]] = {
    LocalStorage
      .handlerWithoutEvents(keys.graphChanges)
      .unsafeRunSync()
      .mapHandler[List[GraphChanges]](changes => Option(toJson(changes)))(_.flatMap(fromJson[List[GraphChanges]]).getOrElse(Nil))
  }

  val sidebarOpen: Var[Option[Boolean]] = {
    LocalStorage
      .handlerWithoutEvents(keys.sidebarOpen)
      .unsafeRunSync()
      .mapHandler[Option[Boolean]](open => Option(toJson(open)))(_.flatMap(fromJson[Boolean]))
      .unsafeToVar(internal(keys.sidebarOpen).flatMap(fromJson[Boolean]))
  }

  val backendTimeDelta: Var[Long] = {
    LocalStorage
      .handlerWithoutEvents(keys.backendTimeDelta)
      .unsafeRunSync()
      .mapHandler[Long](delta => Option(toJson(delta)))(_.flatMap(fromJson[Long]).getOrElse(0L))
      .unsafeToVar(internal(keys.backendTimeDelta).flatMap(fromJson[Long]).getOrElse(0L))
  }
}
