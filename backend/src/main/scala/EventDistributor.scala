package wust.backend

import wust.api._
import wust.framework._
import collection.mutable
import wust.ids._
import wust.db.Db
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success,Failure}

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

    val postIds = events.flatMap(postIdsInEvent _).toSet
    db.post.getGroupIds(postIds).onComplete {
      case Success(postGroups) =>
        val receivers = subscribers - sender
        sender.send(RequestEvent(nonGraphEvents, postGroups))
        receivers.foreach(_.send(RequestEvent(events, postGroups)))
      case Failure(t) =>
        scribe.error(s"Error while getting post groups for events: $events")
        scribe.error(t)
    }
  }

  def postIdsInEvent(event: ApiEvent): Set[PostId] = event match {
    case NewGraphChanges(changes) => changes.addPosts.map(_.id) ++ changes.updatePosts.map(_.id) ++ changes.delPosts
    case _ => Set.empty
  }
}
