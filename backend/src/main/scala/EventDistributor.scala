package wust.backend

import wust.api._
import wust.framework._
import collection.mutable

class EventDistributor {
  val subscribers = mutable.HashSet.empty[EventSender[ApiEvent]]

  def subscribe(sender: EventSender[ApiEvent]) {
    subscribers += sender
  }

  def unsubscribe(sender: EventSender[ApiEvent]) {
    subscribers -= sender
  }

  def publish(sender: Option[EventSender[ApiEvent]], event: ApiEvent) {
    scribe.info(s"--> Backend Event: $event --> ${subscribers.size} connectedClients")
    val receivers = subscribers -- sender
    receivers.foreach(_.send(event))
  }
}
