package wust.backend

import akka.event.{EventBus, LookupClassification}
import akka.http.scaladsl.model.ws.Message
import wust.api._
import wust.framework._

sealed trait Channel
object Channel {
  case object Graph extends Channel
  case class User(id: Long) extends Channel
  case class UserGroup(id: Long) extends Channel
}

case class ChannelEvent(channel: Channel, event: ApiEvent)
case class SerializedChannelEvent(channel: Channel, payload: Message)

trait EventDispatcher {
  def subscribe(sender: EventSender[ApiEvent], channel: Channel): Boolean
  def unsubscribe(sender: EventSender[ApiEvent], channel: Channel): Boolean
  def unsubscribe(sender: EventSender[ApiEvent]): Unit
  def publish(event: SerializedChannelEvent): Unit
}

class ChannelEventBus extends EventBus with LookupClassification with EventDispatcher {
  type Event = SerializedChannelEvent
  type Classifier = Channel
  type Subscriber = EventSender[ApiEvent]

  protected def classify(event: Event): Classifier = event.channel
  protected def publish(ev: Event, subscriber: Subscriber): Unit = subscriber.send(ev.payload)
  protected def compareSubscribers(a: Subscriber, b: Subscriber): Int = a.compareTo(b)
  protected def mapSize: Int = 128 // expected size of classifiers
}
