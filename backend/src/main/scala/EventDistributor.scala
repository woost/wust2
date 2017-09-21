package wust.backend

import wust.api._
import wust.framework._
import collection.mutable
import wust.ids._
import wust.db.Db
import wust.graph._
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success,Failure}
import scala.util.control.NonFatal
import DbConversions._

case class RequestEvent(events: Seq[ApiEvent.Public], postGroups: Map[PostId, Set[GroupId]])

class EventDistributor(db: Db) {
  val subscribers = mutable.HashSet.empty[NotifiableClient[RequestEvent]]

  def subscribe(client: NotifiableClient[RequestEvent]) {
    subscribers += client
  }

  def unsubscribe(client: NotifiableClient[RequestEvent]) {
    subscribers -= client
  }

  def publish(origin: ClientIdentity, events: Seq[ApiEvent.Public])(implicit ec: ExecutionContext) {
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
