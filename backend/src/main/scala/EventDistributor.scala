package wust.backend

import wust.api._
import wust.framework._
import collection.mutable
import wust.ids._
import wust.db.Db
import scala.concurrent.{Future, ExecutionContext}

class EventDistributor(db: Db) {
  val subscribers = mutable.HashSet.empty[EventSender[RequestEvent]]

  def subscribe(sender: EventSender[RequestEvent]) {
    subscribers += sender
  }

  def unsubscribe(sender: EventSender[RequestEvent]) {
    subscribers -= sender
  }

  def publish(sender: EventSender[RequestEvent], events: Seq[ApiEvent])(implicit ec: ExecutionContext) {
    scribe.info(s"--> Backend Events: $events --> ${subscribers.size} connectedClients")

    // do not send graphchange events to origin of event
    val nonGraphEvents = events.filter {
      case NewGraphChanges(_) => false
      case _ => true
    }

    val receivers = subscribers - sender
    postGroupsFromEvents(events).foreach { postGroups =>
      sender.send(RequestEvent(nonGraphEvents, postGroups))
      receivers.foreach(_.send(RequestEvent(events, postGroups)))
    }
  }

  def postIdsInEvent(event: ApiEvent): Set[PostId] = event match {
    case NewGraphChanges(changes) => changes.addPosts.map(_.id) ++ changes.updatePosts.map(_.id) ++ changes.delPosts
    case _ => Set.empty
  }

  def postGroupsFromEvents(events: Seq[ApiEvent])(implicit ec: ExecutionContext): Future[Map[PostId, Set[GroupId]]] = {
    val postIds = events.flatMap(postIdsInEvent _)

    db.ctx.transaction { implicit ec =>
      //TODO: we know the groups for addPosts already => addOwnerships
      //TODO dont query for each post? needs to be chained
      postIds.foldLeft(Future.successful(Map.empty[PostId, Set[GroupId]])) { (postGroups, postId) =>
        postGroups.flatMap { postGroups =>
          db.post.getGroups(postId).map(groups => postGroups + (postId -> groups.map(_.id).toSet))
        }
      }
    }
  }
}
