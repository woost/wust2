package framework.message

import boopickle.Default._
import java.nio.ByteBuffer

//TODO: fix double serialization of messages through autowire

sealed trait ClientMessage
case class Subscribe[CHANNEL](channel: CHANNEL) extends ClientMessage
case class CallRequest(seqId: Int, path: Seq[String], args: Map[String, ByteBuffer]) extends ClientMessage

sealed trait ServerMessage
case class Response(seqId: Int, result: ByteBuffer) extends ServerMessage
case class Notification[CHANNEL,EVENT](channel: CHANNEL, event: EVENT) extends ServerMessage

object ClientMessage {
  def pickler[CHANNEL : Pickler] = compositePickler[ClientMessage]
    .addConcreteType[Subscribe[CHANNEL]]
    .addConcreteType[CallRequest]
}

object ServerMessage {
  def pickler[CHANNEL : Pickler, EVENT : Pickler] = compositePickler[ServerMessage]
    .addConcreteType[Response]
    .addConcreteType[Notification[CHANNEL,EVENT]]
}
