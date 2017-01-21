package framework.message

import boopickle.Default._
import java.nio.ByteBuffer

class Messages[CHANNEL: Pickler, EVENT: Pickler, ERROR: Pickler, AUTH: Pickler] {
  sealed trait Control
  case class Login(auth: AUTH) extends Control
  case class Logout() extends Control

  //TODO: fix double serialization of request/response through autowire
  sealed trait ClientMessage
  case class CallRequest(seqId: SequenceId, path: Seq[String], args: Map[String, ByteBuffer]) extends ClientMessage
  case class ControlRequest(seqId: SequenceId, control: Control) extends ClientMessage
  case class Subscription(channel: CHANNEL) extends ClientMessage

  sealed trait ServerMessage
  case class CallResponse(seqId: SequenceId, result: Either[ERROR,ByteBuffer]) extends ServerMessage
  case class ControlResponse(seqId: SequenceId, success: Boolean) extends ServerMessage
  case class Notification(event: EVENT) extends ServerMessage

  //TODO: case objects?
  implicit def controlPickler = compositePickler[Control]
    .addConcreteType[Login]
    .addConcreteType[Logout]
  implicit def clientMessagePickler = compositePickler[ClientMessage]
    .addConcreteType[CallRequest]
    .addConcreteType[ControlRequest]
    .addConcreteType[Subscription]
  implicit def ServerMessagePickler = compositePickler[ServerMessage]
    .addConcreteType[CallResponse]
    .addConcreteType[ControlResponse]
    .addConcreteType[Notification]
}
