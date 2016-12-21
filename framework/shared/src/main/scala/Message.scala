package framework.message

import boopickle.Default._
import java.nio.ByteBuffer

object Messages {
  type SequenceId = Int
}

class Messages[CHANNEL: Pickler, EVENT: Pickler] {
  import Messages._

  //TODO: fix double serialization of request/response through autowire
  sealed trait ClientMessage
  case class CallRequest(seqId: SequenceId, path: Seq[String], args: Map[String, ByteBuffer]) extends ClientMessage
  case class Subscription(channel: CHANNEL) extends ClientMessage

  sealed trait ServerMessage
  case class Response(seqId: SequenceId, result: ByteBuffer) extends ServerMessage
  case class BadRequest(seqId: SequenceId, error: String) extends ServerMessage
  case class Notification(event: EVENT) extends ServerMessage

  implicit def clientMessagePickler = compositePickler[ClientMessage]
    .addConcreteType[CallRequest]
    .addConcreteType[Subscription]
  implicit def ServerMessagePickler = compositePickler[ServerMessage]
    .addConcreteType[Response]
    .addConcreteType[BadRequest]
    .addConcreteType[Notification]
}
