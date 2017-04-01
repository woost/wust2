package wust.framework

import akka.event.{LookupClassification, EventBus}
import akka.actor._

import message.Messages
import akka.http.scaladsl.model.ws.Message

trait Dispatcher[Channel, Event] {
  def subscribe(actor: ActorRef, channel: Channel): Unit
  def unsubscribe(actor: ActorRef, channel: Channel): Unit
  def unsubscribe(actor: ActorRef): Unit
  def emit(channel: Channel, event: Event): Unit
}

class EventDispatcher[Channel, Event](messages: Messages[Channel, Event, _, _, _]) extends Dispatcher[Channel, Event] {
  import messages._
  import WebsocketSerializer.serialize

  private val eventBus = new ChannelEventBus[Channel, Message]

  def subscribe(actor: ActorRef, channel: Channel) = eventBus.subscribe(actor, channel)
  def unsubscribe(actor: ActorRef, channel: Channel) = eventBus.unsubscribe(actor, channel)
  def unsubscribe(actor: ActorRef) = eventBus.unsubscribe(actor)

  def emit(channel: Channel, event: Event): Unit = {
    scribe.info(s"-[$channel]-> event: $event")
    val payload = serialize[ServerMessage](Notification(event))
    eventBus.publish(ChannelEvent(channel, payload))
  }
}

case class ChannelEvent[Channel, Payload](channel: Channel, payload: Payload)
class ChannelEventBus[Channel, Payload] extends EventBus with LookupClassification {
  type Event = ChannelEvent[Channel, Payload]
  type Classifier = Channel
  type Subscriber = ActorRef

  protected def classify(event: Event): Classifier = event.channel
  protected def publish(event: Event, subscriber: Subscriber): Unit = subscriber ! event.payload
  protected def compareSubscribers(a: Subscriber, b: Subscriber): Int = a.compareTo(b)
  protected def mapSize: Int = 128 // expected size of classifiers
}
