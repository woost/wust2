package wust.webApp.jsdom

import cats.data.OptionT
import cats.implicits._
import org.scalajs.dom.raw.{IDBDatabase, IDBFactory, IDBObjectStore, IDBRequest}
import org.scalajs.dom.window
import wust.api._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object IndexedDbOps {
  private val indexedDb = window.indexedDB.asInstanceOf[js.UndefOr[IDBFactory]].toOption

  object stores {
    val auth = "auth"
  }

  private lazy val db: OptionT[Future, IDBDatabase] = OptionT(
    indexedDb.fold(Future.successful(Option.empty[IDBDatabase])) { indexedDb =>
      val openreq = indexedDb.open("woost", 1)
      openreq.onupgradeneeded = { e =>
        val db = openreq.result.asInstanceOf[IDBDatabase]
        db.createObjectStore(stores.auth)
      }
      requestFuture[IDBDatabase](openreq)
    }
  )

  def storeAuth(auth: Authentication)(implicit ec: ExecutionContext): Future[Boolean] = auth match {
    case Authentication.Verified(_, _, token) =>
      onStore(stores.auth) { store =>
        store.put(token, 0)
      }
    case _ =>
      onStore(stores.auth) { store =>
        store.delete(0)
      }
  }

  private def onStore(
      storeName: String
  )(f: IDBObjectStore => IDBRequest)(implicit ec: ExecutionContext): Future[Boolean] =
    db.flatMapF { db =>
        val transaction = db.transaction(Array(storeName).toJSArray, "readwrite")
        val store = transaction.objectStore(storeName)
        requestFuture(f(store))
      }
      .value
      .map((opt: Option[Any]) => opt.isDefined)
      .recover { case _ => false }

  private def requestFuture[T](request: IDBRequest): Future[Option[T]] = {
    val promise = Promise[Option[T]]()
    request.onsuccess = { _ =>
      promise success Option(request.result.asInstanceOf[T])
    }
    request.onerror = { _ =>
      scribe.warn(s"IndexedDb request failed: ${request.error}")
      promise success None
    }
    promise.future
  }
}
