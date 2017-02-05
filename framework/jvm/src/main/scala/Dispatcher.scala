package framework

import akka.event.{LookupClassification, EventBus}
import akka.actor._

class Dispatcher[CHANNEL, PAYLOAD] extends EventBus with LookupClassification {
  import Dispatcher._

  type Event = ChannelEvent[CHANNEL, PAYLOAD]
  type Classifier = CHANNEL
  type Subscriber = ActorRef

  protected def classify(event: Event): Classifier = event.channel
  protected def publish(event: Event, subscriber: Subscriber): Unit = subscriber ! event.payload
  protected def compareSubscribers(a: Subscriber, b: Subscriber): Int = a.compareTo(b)
  protected def mapSize: Int = 128 // expected size of classifiers
}
object Dispatcher {
  case class ChannelEvent[CHANNEL, PAYLOAD](channel: CHANNEL, payload: PAYLOAD)
}
