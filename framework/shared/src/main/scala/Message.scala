package framework.message

import boopickle.Default._
import java.nio.ByteBuffer

//TODO: fix double serialization of messages through autowire
trait Messages[CHANNEL,EVENT] {
  type SequenceId = Int

  sealed trait ClientMessage
  case class CallRequest(seqId: SequenceId, path: Seq[String], args: Map[String, ByteBuffer]) extends ClientMessage
  case class Subscription(channel: CHANNEL) extends ClientMessage

  sealed trait ServerMessage
  case class Response(seqId: SequenceId, result: ByteBuffer) extends ServerMessage
  case class Notification(event: EVENT) extends ServerMessage

  implicit def channelPickler: Pickler[CHANNEL]
  implicit def eventPickler: Pickler[EVENT]
  implicit def clientMessagePickler = compositePickler[ClientMessage]
    .addConcreteType[CallRequest]
    .addConcreteType[Subscription]
  implicit def ServerMessagePickler = compositePickler[ServerMessage]
    .addConcreteType[Response]
    .addConcreteType[Notification]
}
