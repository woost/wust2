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

  def publish(sender: EventSender[ApiEvent], events: Seq[ApiEvent]) {
    scribe.info(s"--> Backend Events: $events --> ${subscribers.size} connectedClients")

    // do not send graphchange events to origin of event
    val nonGraphEvents = events.filter {
      case NewGraphChanges(_) => false
      case _ => true
    }

    val receivers = subscribers - sender
    sender.send(nonGraphEvents)
    receivers.foreach(_.send(events))
  }
}
