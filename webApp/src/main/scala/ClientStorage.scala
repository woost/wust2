package wust.webApp

import io.circe._, io.circe.generic.extras.semiauto._, io.circe.parser._, io.circe.syntax._
import outwatch.dom.Handler
import outwatch.util.LocalStorage
import rx._
import wust.api.Authentication
import wust.api.serialize.Circe._
import wust.graph.GraphChanges
import wust.webApp.outwatchHelpers._ //TODO use outwatch.util.Storage(dom.Storage)

class ClientStorage(implicit owner: Ctx.Owner) {
  private val internal = org.scalajs.dom.ext.LocalStorage

  object keys {
    val auth = "wust.auth"
    val sidebarOpen = "wust.sidebar.open"
    val graphChanges = "wust.graph.changes"
  }

  private def toJson[T: Encoder](value: T): String = value.asJson.noSpaces
  private def fromJson[T: Decoder](value: String): Option[T] = decode[T](value).right.toOption

  //TODO: howto handle with events from other tabs?
  val auth: Var[Option[Authentication]] = {
    LocalStorage
      .handlerWithoutEvents(keys.auth)
      .unsafeRunSync()
      .imap(_.flatMap(fromJson[Authentication]))(auth => Option(toJson(auth)))
      .unsafeToVar(internal(keys.auth).flatMap(fromJson[Authentication]))
  }

  //TODO: howto handle with events from other tabs?
  val graphChanges: Handler[List[GraphChanges]] = {
    LocalStorage
      .handlerWithoutEvents(keys.graphChanges)
      .unsafeRunSync()
      .imap(_.flatMap(fromJson[List[GraphChanges]]).getOrElse(Nil))(
        changes => Option(toJson(changes))
      )
  }

  val sidebarOpen: Var[Boolean] = {
    LocalStorage
      .handlerWithoutEvents(keys.sidebarOpen)
      .unsafeRunSync()
      .imap(_.flatMap(fromJson[Boolean]).getOrElse(false))(open => Option(toJson(open)))
      .unsafeToVar(internal(keys.sidebarOpen).flatMap(fromJson[Boolean]).getOrElse(false))
  }
}
