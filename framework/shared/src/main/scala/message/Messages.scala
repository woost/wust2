package wust.framework.message

import java.nio.ByteBuffer

import boopickle.Default._

class Messages[Event : Pickler, Failure: Pickler] {
  //TODO: fix double serialization of request/response through autowire
  // the map corresponds to the arguments for the called api method
  // maybe generic over h-list like autowire?
  sealed trait ClientMessage
  case class Ping() extends ClientMessage
  case class CallRequest(seqId: SequenceId, path: Seq[String], args: Map[String, ByteBuffer]) extends ClientMessage

  sealed trait ServerMessage
  case class Pong() extends ServerMessage
  case class CallResponse(seqId: SequenceId, result: Either[Failure, ByteBuffer]) extends ServerMessage
  case class Notification(event: List[Event]) extends ServerMessage

  //TODO: case objects?
  implicit def clientMessagePickler = compositePickler[ClientMessage]
    .addConcreteType[Ping]
    .addConcreteType[CallRequest]
  implicit def ServerMessagePickler = compositePickler[ServerMessage]
    .addConcreteType[Pong]
    .addConcreteType[CallResponse]
    .addConcreteType[Notification]
}
