package wust.backend

import wust.api._
import wust.db.Db
import wust.ids._
import mycelium.server._

import scala.collection.mutable
import scala.concurrent.{Future, ExecutionContext}
import scala.util.Failure

case class RequestEvent(events: Seq[ApiEvent]) extends AnyVal

class EventDistributor(db: Db) {
  val subscribers = mutable.HashSet.empty[NotifiableClient[RequestEvent]]

  def subscribe(client: NotifiableClient[RequestEvent]): Unit = {
    subscribers += client
  }

  def unsubscribe(client: NotifiableClient[RequestEvent]): Unit = {
    subscribers -= client
  }

  def publish(origin: NotifiableClient[RequestEvent], events: Seq[ApiEvent.Public])(implicit ec: ExecutionContext): Unit = if (events.nonEmpty) {
    scribe.info(s"Backend events (${subscribers.size - 1} clients): $events")

    subscribers.foreach { client =>
      if (client != origin) client.notify(RequestEvent(events))
    }
  }
}
