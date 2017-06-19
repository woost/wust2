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
    val graphChanges = events.collect { case NewGraphChanges(changes) => changes }
    val nonGraphEvents = events.filter {
      case NewGraphChanges(_) => false
      case _ => true
    }

    val graphEvent = if (graphChanges.nonEmpty) {
      val changes = graphChanges.reduce(_ + _)
      if (changes.undeletePosts.nonEmpty)
        db.post.get(changes.undeletePosts).map { posts =>
          Option(NewGraphChanges(changes.copyF(undeletePosts = _ => Set.empty, addPosts = _ ++ posts.map(forClient _))))
        }
      else Future.successful(Option(NewGraphChanges(changes)))
    } else Future.successful(None)

    val postIds = events.flatMap(postIdsInEvent _).toSet
    val result = for {
      postGroups <- db.post.getGroupIds(postIds)
      graphEvent <- graphEvent
    } yield {
      val receivers = subscribers - sender
      val allEvents = nonGraphEvents ++ graphEvent.toSet
      //TODO needs graph events for undeleted posts
      // sender.send(RequestEvent(nonGraphEvents, postGroups))
      sender.send(RequestEvent(allEvents, postGroups))
      receivers.foreach(_.send(RequestEvent(allEvents, postGroups)))
    }

    result.recover {
      case NonFatal(t) =>
        scribe.error(s"Error while getting meta info for events: $events")
        scribe.error(t)
    }
  }

  def postIdsInEvent(event: ApiEvent): Set[PostId] = event match {
    case NewGraphChanges(changes) => changes.addPosts.map(_.id) ++ changes.updatePosts.map(_.id) ++ changes.delPosts
    case _ => Set.empty
  }
}
