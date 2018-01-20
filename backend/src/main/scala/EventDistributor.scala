package wust.backend

import wust.api._
import wust.db.Db
import wust.ids._
import mycelium.server._

import scala.collection.mutable
import scala.concurrent.{Future, ExecutionContext}
import scala.util.Failure

case class RequestEvent(events: Seq[ApiEvent.Public], postGroups: Map[PostId, Set[GroupId]])

class EventDistributor(db: Db) {
  val subscribers = mutable.HashSet.empty[NotifiableClient[RequestEvent]]

  def subscribe(client: NotifiableClient[RequestEvent]): Unit = {
    subscribers += client
  }

  def unsubscribe(client: NotifiableClient[RequestEvent]): Unit = {
    subscribers -= client
  }

  def publish(origin: NotifiableClient[RequestEvent], events: Seq[ApiEvent.Public])(implicit ec: ExecutionContext): Unit = if (events.nonEmpty) {
    scribe.info(s"--> Backend Events: $events --> ${subscribers.size} connectedClients")

    val postIds = events.flatMap(postIdsInEvent).toSet
    for {
      postGroups <- getGroupIds(postIds)
    } subscribers.foreach { client =>
      if (client != origin) client.notify(RequestEvent(events, postGroups))
    }
  }

  private def getGroupIds(postIds: Set[PostId])(implicit ec: ExecutionContext): Future[Map[PostId, Set[GroupId]]] = {
    val groups = db.post.getGroupIds(postIds)
    groups.onComplete {
      case Failure(t) =>
        scribe.error(s"Error while getting post groups for posts: $postIds")
        scribe.error(t)
      case _ =>
    }

    groups
  }

  private def postIdsInEvent(event: ApiEvent): Set[PostId] = event match {
    case ApiEvent.NewGraphChanges(changes) => changes.addPosts.map(_.id) ++ changes.updatePosts.map(_.id) ++ changes.delPosts
    case _ => Set.empty
  }
}
