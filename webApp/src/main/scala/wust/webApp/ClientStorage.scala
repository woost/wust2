package wust.webApp

import wust.util.Memo
import wust.ids._
import collection.mutable
import cats.effect.SyncIO
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import outwatch.ext.monix._
import outwatch.ext.monix.handler._
import outwatch.ext.monix.util.LocalStorage
import rx._
import wust.api.Authentication
import wust.api.serialize.Circe._
import wust.graph.GraphChanges
import wust.webUtil.outwatchHelpers._

import scala.util.{Failure, Success, Try}
import wust.facades.segment.Segment

class ClientStorage(implicit owner: Ctx.Owner) {
  import org.scalajs.dom.ext.{LocalStorage => internal}

  object keys {
    val auth = "wust.auth"
    val sidebarOpen = "wust.sidebar.open"
    val sidebarWithProjects = "wust.sidebar.projects"
    val taglistOpen = "wust.taglist.open"
    val filterlistOpen = "wust.filterlist.open"
    def pendingChanges(userId:UserId) = s"wust.pendingchanges.${userId.toUuid.toString}"
    def pendingChangesInvalid(userId:UserId) = s"wust.pendingchanges.invalid.${userId.toUuid.toString}"
    val backendTimeDelta = "wust.backendtimedelta"
  }

  private def toJson[T: Encoder](value: T): String = value.asJson.noSpaces
  private def fromJson[T: Decoder](value: String): Option[T] = decode[T](value).right.toOption

  val canAccessLs: Boolean = {
    Try {
      val woostStr = "wust-localstorage-write-test"
      internal.update(woostStr, woostStr)
      internal.remove(woostStr)
    } match {
      case Success(_) =>
        true
      case Failure(e) =>
        Segment.trackError("Localstorage Access-Error", e.getMessage)
        scribe.warn("Localstorage is not accessible.")
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

  def addPendingGraphChange(userId: UserId, change:GraphChanges) = {
    pendingChanges(userId).update(_ :+ toJson(change))
  }

  def clearPendingGraphChanges(userId: UserId) = {
    pendingChanges(userId).update(_ => List.empty)
  }

  def getDecodablePendingGraphChanges(userId: UserId):List[GraphChanges] = {
    val all = pendingChanges(userId).now
    val validEncodedBuilder = mutable.ListBuffer.empty[String]
    val validDecodedBuilder = mutable.ListBuffer.empty[GraphChanges]
    val invalidBuilder = mutable.ListBuffer.empty[String]
    all.foreach { encoded =>
      decode[GraphChanges](encoded) match {
        case Left(error) => 
          invalidBuilder += encoded
          Segment.trackError("Failed to decode pending GraphChange", s"${error.toString}: ${encoded}")
        case Right(valid) => 
          validEncodedBuilder += encoded
          validDecodedBuilder += valid
      }
    }

    val invalid = invalidBuilder.result()

    if(invalid.nonEmpty) {
      pendingChangesInvalid(userId).update(_ ++ invalid)
      pendingChanges(userId)() = validEncodedBuilder.result()
    }

    validDecodedBuilder.result()
  }


  val pendingChanges = Memo.mutableHashMapMemo[UserId, Var[List[String]]](pendingChangesByUser)
  val pendingChangesInvalid = Memo.mutableHashMapMemo[UserId, Var[List[String]]](pendingChangesInvalidByUser)

  //TODO: howto handle with events from other tabs?
  private def pendingChangesByUser(userId: UserId): Var[List[String]] = {
    val storageKey = keys.pendingChanges(userId)
    if(canAccessLs) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](storageKey)
        .unsafeRunSync()
        .mapHandler[List[String]](changes => Option(toJson(changes)))(_.flatMap(fromJson[List[String]]).getOrElse(List.empty))
        .unsafeToVar(internal(storageKey).flatMap(fromJson[List[String]]).getOrElse(List.empty))
    } else Var(List.empty)
  }

  private def pendingChangesInvalidByUser(userId: UserId): Var[List[String]] = {
    val storageKey = keys.pendingChangesInvalid(userId)
    if(canAccessLs) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](storageKey)
        .unsafeRunSync()
        .mapHandler[List[String]](changes => Option(toJson(changes)))(_.flatMap(fromJson[List[String]]).getOrElse(List.empty))
        .unsafeToVar(internal(storageKey).flatMap(fromJson[List[String]]).getOrElse(List.empty))
    } else Var(List.empty)
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
