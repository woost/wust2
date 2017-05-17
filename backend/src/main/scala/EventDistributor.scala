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

  def publish(event: ApiEvent) {
    subscribers.foreach(_.send(event))
  }
}
