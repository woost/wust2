package wust.backend

import wust.api._
import wust.db.Db
import wust.ids._
import mycelium.server._

import scala.collection.mutable
import scala.concurrent.{Future, ExecutionContext}
import scala.util.Failure

class EventDistributor(db: Db) {
  val subscribers = mutable.HashSet.empty[NotifiableClient[ApiEvent]]

  def subscribe(client: NotifiableClient[ApiEvent]): Unit = {
    subscribers += client
  }

  def unsubscribe(client: NotifiableClient[ApiEvent]): Unit = {
    subscribers -= client
  }

  def publish(origin: NotifiableClient[ApiEvent], events: List[ApiEvent.Public])(implicit ec: ExecutionContext): Unit = if (events.nonEmpty) {
    scribe.info(s"Backend events (${subscribers.size - 1} clients): $events")

    subscribers.foreach { client =>
      if (client != origin) client.notify(events)
    }
  }
}
