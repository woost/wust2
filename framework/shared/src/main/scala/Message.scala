package framework.message

import boopickle.Default._
import java.nio.ByteBuffer

object Messages {
  type SequenceId = Int
}

class Messages[CHANNEL: Pickler, EVENT: Pickler, ERROR: Pickler, AUTH: Pickler] {
  import Messages._

  //TODO: fix double serialization of request/response through autowire
  sealed trait ClientMessage
  case class CallRequest(seqId: SequenceId, path: Seq[String], args: Map[String, ByteBuffer]) extends ClientMessage
  case class Login(auth: AUTH) extends ClientMessage
  case class Logout() extends ClientMessage
  case class Subscription(channel: CHANNEL) extends ClientMessage

  sealed trait ServerMessage
  case class Response(seqId: SequenceId, result: ByteBuffer) extends ServerMessage
  case class BadRequest(seqId: SequenceId, error: ERROR) extends ServerMessage
  case class LoggedIn() extends ServerMessage
  case class LoginFailed() extends ServerMessage
  case class LoggedOut() extends ServerMessage
  case class Notification(event: EVENT) extends ServerMessage

  //TODO: case objects?
  implicit def clientMessagePickler = compositePickler[ClientMessage]
    .addConcreteType[CallRequest]
    .addConcreteType[Subscription]
    .addConcreteType[Login]
    .addConcreteType[Logout]
  implicit def ServerMessagePickler = compositePickler[ServerMessage]
    .addConcreteType[Response]
    .addConcreteType[BadRequest]
    .addConcreteType[Notification]
    .addConcreteType[LoggedIn]
    .addConcreteType[LoginFailed]
    .addConcreteType[LoggedOut]
}
