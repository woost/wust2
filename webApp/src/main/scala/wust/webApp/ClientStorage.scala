package wust.webApp

import wust.facades.jsSha256.Sha256
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

  private def graphChangeToHash(json:String):String = Sha256.sha224(json)

  def addPendingGraphChange(userId: UserId, change:GraphChanges) = {
    val changeJson = toJson(change)
    val key = graphChangeToHash(changeJson)
    pendingChanges(userId).update(_.updated(key, changeJson))
  }

  def deletePendingGraphChanges(userId: UserId, changes:GraphChanges) = {
    val changeJson = toJson(changes)
    val key = graphChangeToHash(changeJson)
    pendingChanges(userId).update(_ - key)
  }

  def getDecodablePendingGraphChanges(userId: UserId):List[GraphChanges] = {
    val all = pendingChanges(userId).now
    val invalidKeysBuilder = mutable.ListBuffer.empty[String]
    val validDecodedBuilder = mutable.ListBuffer.empty[GraphChanges]
    all.foreach { case(key, encoded) =>
      decode[GraphChanges](encoded) match {
        case Left(error) => 
          invalidKeysBuilder += key
          val errorId = NodeId.fresh().toUuid.toString
          Segment.trackError("Failed to decode pending GraphChange", s"${errorId}")
          Client.api.log(s"Failed to decode pending GraphChange: errorId=$errorId, msg=$error, change=$encoded")
        case Right(valid) => 
          validDecodedBuilder += valid
      }
    }

    val validDecoded = validDecodedBuilder.result()

    if(all.size != validDecoded.size) {
      pendingChanges(userId).update(_ -- invalidKeysBuilder.result())
    }

    validDecoded
  }


  val pendingChanges = Memo.mutableHashMapMemo[UserId, Var[Map[String, String]]](pendingChangesByUser)

  //TODO: howto handle with events from other tabs?
  private def pendingChangesByUser(userId: UserId): Var[Map[String, String]] = {
    val storageKey = keys.pendingChanges(userId)
    if(canAccessLs) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](storageKey)
        .unsafeRunSync()
        .mapHandler[Map[String, String]](changes => Option(toJson(changes)))(_.flatMap(fromJson[Map[String, String]]).getOrElse(Map.empty))
        .unsafeToVar(internal(storageKey).flatMap(fromJson[Map[String, String]]).getOrElse(Map.empty))
    } else Var(Map.empty)
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
