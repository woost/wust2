package wust.webApp

import scala.scalajs.js.typedarray._
import wust.webApp.jsdom.Base64Codec
import wust.api.serialize.Boopickle._
import boopickle.Default._
import wust.facades.jsSha256.Sha256
import wust.util.Memo
import wust.ids._
import wust.graph.Graph
import wust.graph.Page
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
import wust.webUtil.BinaryConvertors._
import wust.util.time.time

import scala.util.{ Failure, Success, Try }
import wust.facades.segment.Segment
import scala.scalajs.js.typedarray.Uint8Array
import java.nio.ByteBuffer

class ClientStorage(implicit owner: Ctx.Owner) {
  import org.scalajs.dom.ext.{ LocalStorage => internal }

  object keys {
    val auth = "wust.auth"
    val sidebarOpen = "wust.sidebar.open"
    val sidebarWithProjects = "wust.sidebar.projects"
    val taglistOpen = "wust.taglist.open"
    val filterlistOpen = "wust.filterlist.open"
    def pendingChanges(userId: UserId) = s"wust.pendingchanges.${userId.toUuid.toString}"
    val backendTimeDelta = "wust.backendtimedelta"
    def pageCache(page: Page) = new {
      val baseKey = page.parentId match {
        case Some(parentId) => s"wust.pageCache.${parentId.toUuid.toString}"
        case None           => s"wust.pageCache.empty"
      }
      val graph = s"${baseKey}.graph"
      val time = s"${baseKey}.time"
      val keys = List(graph, time)
    }
  }

  private def toJson[T: Encoder](value: T): String = value.asJson.noSpaces
  private def fromJson[T: Decoder](value: String): Option[T] = decode[T](value).right.toOption

  val canAccessStorage: Boolean = {
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

  def getGraph(page: Page): Option[Graph] = {
    val storageKey = keys.pageCache(page)
    val encodedOpt = internal(storageKey.graph)
    encodedOpt flatMap { encoded =>
      decode[Graph](encoded) match {
        case Left(_) =>
          // cannot decode cache -> prune this entry
          storageKey.keys.foreach(internal.remove)
          None
        case Right(decoded) => Some(decoded)
      }
    }
  }

  def updateGraph(page: Page, graph: Graph): Unit = {
    time(s"writing to cache: $page") {
      val storageKey = keys.pageCache(page)
      internal.update(storageKey.graph, toJson(graph))
      internal.update(storageKey.time, EpochMilli.now.toString)
    }
  }

  val auth: Var[Option[Authentication]] = {
    if (canAccessStorage) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](keys.auth)
        .unsafeRunSync()
        .mapHandler[Option[Authentication]](auth => Option(toJson(auth)))(_.flatMap(fromJson[Authentication]))
        .unsafeToVar(internal(keys.auth).flatMap(fromJson[Authentication]))
    } else Var(None)
  }


  def compress(data: Uint8Array):Uint8Array = ???
  private def encodeBoopickleBase64[T: Pickler](data: T): String = {
    val serialized:ByteBuffer = Pickle.intoBytes(data)
    val compressed = compress(serialized.toUint8Array)
    Base64Codec.encode(compressed)
  }
  private def decodeBoopickleBase64[T: Pickler](encoded: String): Option[T] = Try(Unpickle[T].fromBytes(Base64Codec.decode(encoded))).toOption



  private def graphChangeToHash(serialized: String): String = Sha256.sha224(serialized)
  private def encodeGraphChangesBoopickleBase64(change: GraphChanges): String = Base64Codec.encode(Pickle.intoBytes(change).toUint8Array)
  private def decodeGraphChangesBoopickleBase64(encoded: String): Option[GraphChanges] = Try(Unpickle[GraphChanges].fromBytes(Base64Codec.decode(encoded))).toOption

  def addPendingGraphChange(userId: UserId, change: GraphChanges) = {
    val changeSerialized = encodeGraphChangesBoopickleBase64(change)
    val key = graphChangeToHash(changeSerialized)
    pendingChanges(userId).update(_.updated(key, changeSerialized))
  }

  def deletePendingGraphChanges(userId: UserId, changes: GraphChanges) = {
    val changeSerialized = encodeGraphChangesBoopickleBase64(changes)
    val key = graphChangeToHash(changeSerialized)
    pendingChanges(userId).update(_ - key)
  }

  def getDecodablePendingGraphChanges(userId: UserId): List[GraphChanges] = {
    val all = pendingChanges(userId).now
    val invalidKeysBuilder = mutable.ListBuffer.empty[String]
    val validDecodedBuilder = mutable.ListBuffer.empty[GraphChanges]
    all.foreach {
      case (key, encoded) =>
        decodeGraphChangesBoopickleBase64(encoded) match {
          case None =>
            invalidKeysBuilder += key
            val errorId = NodeId.fresh().toUuid.toString
            Segment.trackError("Failed to decode pending GraphChange", s"${errorId}")
            Client.api.log(s"Failed to decode pending GraphChange: errorId=$errorId, change=$encoded")
          case Some(valid) =>
            validDecodedBuilder += valid
        }
    }

    val validDecoded = validDecodedBuilder.result()

    if (all.size != validDecoded.size) {
      pendingChanges(userId).update(_ -- invalidKeysBuilder.result())
    }

    validDecoded
  }

  val pendingChanges = Memo.mutableHashMapMemo[UserId, Var[Map[String, String]]](pendingChangesByUser)

  //TODO: howto handle with events from other tabs?
  private def pendingChangesByUser(userId: UserId): Var[Map[String, String]] = {
    val storageKey = keys.pendingChanges(userId)
    if (canAccessStorage) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](storageKey)
        .unsafeRunSync()
        .mapHandler[Map[String, String]](changes => Option(toJson(changes)))(_.flatMap(fromJson[Map[String, String]]).getOrElse(Map.empty))
        .unsafeToVar(internal(storageKey).flatMap(fromJson[Map[String, String]]).getOrElse(Map.empty))
    } else Var(Map.empty)
  }

  val sidebarOpen: Var[Option[Boolean]] = {
    if (canAccessStorage) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](keys.sidebarOpen)
        .unsafeRunSync()
        .mapHandler[Option[Boolean]](open => Option(toJson(open)))(_.flatMap(fromJson[Boolean]))
        .unsafeToVar(internal(keys.sidebarOpen).flatMap(fromJson[Boolean]))
    } else Var(None)
  }

  val sidebarWithProjects: Var[Option[Boolean]] = {
    if (canAccessStorage) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](keys.sidebarWithProjects)
        .unsafeRunSync()
        .mapHandler[Option[Boolean]](open => Option(toJson(open)))(_.flatMap(fromJson[Boolean]))
        .unsafeToVar(internal(keys.sidebarWithProjects).flatMap(fromJson[Boolean]))
    } else Var(None)
  }

  val taglistOpen: Var[Option[Boolean]] = {
    if (canAccessStorage) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](keys.taglistOpen)
        .unsafeRunSync()
        .mapHandler[Option[Boolean]](open => Option(toJson(open)))(_.flatMap(fromJson[Boolean]))
        .unsafeToVar(internal(keys.taglistOpen).flatMap(fromJson[Boolean]))
    } else Var(None)
  }

  val filterlistOpen: Var[Option[Boolean]] = {
    if (canAccessStorage) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](keys.filterlistOpen)
        .unsafeRunSync()
        .mapHandler[Option[Boolean]](open => Option(toJson(open)))(_.flatMap(fromJson[Boolean]))
        .unsafeToVar(internal(keys.filterlistOpen).flatMap(fromJson[Boolean]))
    } else Var(None)
  }

  val backendTimeDelta: Var[DurationMilli] = {
    if (canAccessStorage) {
      LocalStorage
        .handlerWithoutEvents[SyncIO](keys.backendTimeDelta)
        .unsafeRunSync()
        .mapHandler[DurationMilli](delta => Option(toJson(delta)))(_.flatMap(fromJson[DurationMilli]).getOrElse(DurationMilli(0L)))
        .unsafeToVar(internal(keys.backendTimeDelta).flatMap(fromJson[DurationMilli]).getOrElse(DurationMilli(0L)))
    } else Var(DurationMilli(0L))
  }
}
