package wust.framework.message

import boopickle.Default._
import java.nio.ByteBuffer

class Messages[Channel : Pickler, Event : Pickler, Error: Pickler, Token: Pickler] {
  sealed trait Control
  case class Login(token: Token) extends Control
  case class Logout() extends Control
  case class Subscribe(channel: Channel) extends Control
  case class Unsubscribe(channel: Channel) extends Control

  //TODO: fix double serialization of request/response through autowire
  // the map corresponds to the arguments for the called api method
  // maybe generic over h-list like autowire?
  sealed trait ClientMessage
  case class Ping() extends ClientMessage
  case class CallRequest(seqId: SequenceId, path: Seq[String], args: Map[String, ByteBuffer]) extends ClientMessage
  case class ControlRequest(seqId: SequenceId, control: Control) extends ClientMessage

  sealed trait ServerMessage
  case class Pong() extends ServerMessage
  case class CallResponse(seqId: SequenceId, result: Either[Error, ByteBuffer]) extends ServerMessage
  case class ControlResponse(seqId: SequenceId, success: Boolean) extends ServerMessage
  case class Notification(event: Event) extends ServerMessage

  //TODO: case objects?
  implicit def controlPickler = compositePickler[Control]
    .addConcreteType[Login]
    .addConcreteType[Logout]
    .addConcreteType[Subscribe]
    .addConcreteType[Unsubscribe]
  implicit def clientMessagePickler = compositePickler[ClientMessage]
    .addConcreteType[Ping]
    .addConcreteType[CallRequest]
    .addConcreteType[ControlRequest]
  implicit def ServerMessagePickler = compositePickler[ServerMessage]
    .addConcreteType[Pong]
    .addConcreteType[CallResponse]
    .addConcreteType[ControlResponse]
    .addConcreteType[Notification]
}
