package wust.webApp

import cats.effect.SyncIO
import wust.facades.googleanalytics.GoogleAnalytics
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import outwatch.dom._
import outwatch.ext.monix._
import outwatch.ext.monix.handler._
import outwatch.ext.monix.util.LocalStorage
import rx._
import wust.webUtil.outwatchHelpers._
import wust.api.Authentication
import wust.api.serialize.Circe._
import wust.graph.GraphChanges

import scala.util.{Failure, Success, Try}

class ClientStorage(implicit owner: Ctx.Owner) {
  import org.scalajs.dom.ext.{LocalStorage => internal}

  object keys {
    val auth = "wust.auth"
    val sidebarOpen = "wust.sidebar.open"
    val sidebarWithProjects = "wust.sidebar.projects"
    val taglistOpen = "wust.taglist.open"
    val filterlistOpen = "wust.filterlist.open"
    val graphChanges = "wust.graph.changes"
    val backendTimeDelta = "wust.backendtimedelta"
  }

  private def toJson[T: Encoder](value: T): String = value.asJson.noSpaces
  private def fromJson[T: Decoder](value: String): Option[T] = decode[T](value).right.toOption

  val canAccessLs: Boolean = {
    Try {
      val woostStr = "woost"
      internal.update(woostStr, woostStr)
      internal.remove(woostStr)
    } match {
      case Success(_) =>
        true
      case Failure(_) =>
        GoogleAnalytics.sendEvent("localstorage", "access-error")
        scribe.warn("Beware: Localstorage is not accessible!")
        false
    }
  }

  val auth: Var[Option[Authentication]] = {
    if(canAccessLs) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](keys.auth)
        .unsafeRunSync()
        .mapHandler[Option[Authentication]](auth => Option(toJson(auth)))(_.flatMap(fromJson[Authentication]))
        .unsafeToVar(internal(keys.auth).flatMap(fromJson[Authentication]))
    } else Var(None)
  }

  //TODO: howto handle with events from other tabs?
  val graphChanges: Handler[List[GraphChanges]] = {
    if(canAccessLs) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](keys.graphChanges)
        .unsafeRunSync()
        .mapHandler[List[GraphChanges]](changes => Option(toJson(changes)))(_.flatMap(fromJson[List[GraphChanges]]).getOrElse(Nil))
    } else Handler.unsafe[List[GraphChanges]]
  }

  val sidebarOpen: Var[Option[Boolean]] = {
    if(canAccessLs) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](keys.sidebarOpen)
        .unsafeRunSync()
        .mapHandler[Option[Boolean]](open => Option(toJson(open)))(_.flatMap(fromJson[Boolean]))
        .unsafeToVar(internal(keys.sidebarOpen).flatMap(fromJson[Boolean]))
    } else Var(None)
  }

  val sidebarWithProjects: Var[Option[Boolean]] = {
    if(canAccessLs) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](keys.sidebarWithProjects)
        .unsafeRunSync()
        .mapHandler[Option[Boolean]](open => Option(toJson(open)))(_.flatMap(fromJson[Boolean]))
        .unsafeToVar(internal(keys.sidebarWithProjects).flatMap(fromJson[Boolean]))
    } else Var(None)
  }

  val taglistOpen: Var[Option[Boolean]] = {
    if(canAccessLs) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](keys.taglistOpen)
        .unsafeRunSync()
        .mapHandler[Option[Boolean]](open => Option(toJson(open)))(_.flatMap(fromJson[Boolean]))
        .unsafeToVar(internal(keys.taglistOpen).flatMap(fromJson[Boolean]))
    } else Var(None)
  }

  val filterlistOpen: Var[Option[Boolean]] = {
    if(canAccessLs) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](keys.filterlistOpen)
        .unsafeRunSync()
        .mapHandler[Option[Boolean]](open => Option(toJson(open)))(_.flatMap(fromJson[Boolean]))
        .unsafeToVar(internal(keys.filterlistOpen).flatMap(fromJson[Boolean]))
    } else Var(None)
  }

  val backendTimeDelta: Var[Long] = {
    if(canAccessLs) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](keys.backendTimeDelta)
        .unsafeRunSync()
        .mapHandler[Long](delta => Option(toJson(delta)))(_.flatMap(fromJson[Long]).getOrElse(0L))
        .unsafeToVar(internal(keys.backendTimeDelta).flatMap(fromJson[Long]).getOrElse(0L))
    } else Var(0L)
  }
}
