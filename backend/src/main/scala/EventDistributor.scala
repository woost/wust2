package wust.backend

import wust.api._
import wust.db.Db
import wust.framework._
import wust.ids._

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

case class RequestEvent(events: Seq[ApiEvent.Public], postGroups: Map[PostId, Set[GroupId]])

class EventDistributor(db: Db) {
  val subscribers = mutable.HashSet.empty[NotifiableClient[RequestEvent]]

  def subscribe(client: NotifiableClient[RequestEvent]): Unit = {
    subscribers += client
  }

  def unsubscribe(client: NotifiableClient[RequestEvent]): Unit = {
    subscribers -= client
  }

  def publish(origin: ClientIdentity, events: Seq[ApiEvent.Public])(implicit ec: ExecutionContext): Unit = {
    scribe.info(s"--> Backend Events: $events --> ${subscribers.size} connectedClients")

    val postIds = events.flatMap(postIdsInEvent _).toSet
    val result = for {
      postGroups <- db.post.getGroupIds(postIds)
    } yield {
      subscribers.foreach(_.notify(origin, RequestEvent(events, postGroups)))
    }

    result.recover {
      case NonFatal(t) =>
        scribe.error(s"Error while getting post groups for events: $events")
        scribe.error(t)
    }
  }

  def postIdsInEvent(event: ApiEvent): Set[PostId] = event match {
    case NewGraphChanges(changes) => changes.addPosts.map(_.id) ++ changes.updatePosts.map(_.id) ++ changes.delPosts
    case _ => Set.empty
  }
}
