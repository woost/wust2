package wust.backend

import akka.event.{EventBus, LookupClassification}
import akka.http.scaladsl.model.ws.Message
import wust.ids._
import wust.api._
import wust.framework._

sealed trait Channel
object Channel {
  case object All extends Channel
  case class User(id: UserId) extends Channel
  case class Group(id: GroupId) extends Channel
}

sealed trait BaseChannelEvent { def channel: Channel }
case class ChannelEvent(channel: Channel, event: ApiEvent) extends BaseChannelEvent
case class SerializedChannelEvent(channel: Channel, payload: Message) extends BaseChannelEvent

trait EventDispatcher {
  def subscribe(sender: EventSender[ApiEvent], channel: Channel): Boolean
  def unsubscribe(sender: EventSender[ApiEvent], channel: Channel): Boolean
  def unsubscribe(sender: EventSender[ApiEvent]): Unit
  def publish(event: BaseChannelEvent): Unit
}

class ChannelEventBus extends EventBus with LookupClassification with EventDispatcher {
  type Event = BaseChannelEvent
  type Classifier = Channel
  type Subscriber = EventSender[ApiEvent]

  protected def classify(event: Event): Classifier = event.channel
  protected def publish(ev: Event, subscriber: Subscriber): Unit = ev match {
    case ChannelEvent(_, event) => subscriber.send(event)
    case SerializedChannelEvent(_, payload) => subscriber.sendRaw(payload)
  }
  protected def compareSubscribers(a: Subscriber, b: Subscriber): Int = a.compareTo(b)
  protected def mapSize: Int = 128 // expected size of classifiers
}
