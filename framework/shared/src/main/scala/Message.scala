package framework.message

import boopickle.Default._
import java.nio.ByteBuffer

//TODO: fix double serialization of messages
//TODO: do not pickle twice (and unpickle again), messages should be generic!
//      - boopickle: https://github.com/ochrons/boopickle/issues/69

sealed trait ClientMessage
case class Subscribe(channel: ByteBuffer) extends ClientMessage
case class CallRequest(seqId: Int, path: Seq[String], args: Map[String, ByteBuffer]) extends ClientMessage

sealed trait ServerMessage
case class Response(seqId: Int, result: ByteBuffer) extends ServerMessage
case class Notification(channel: ByteBuffer, ev: ByteBuffer) extends ServerMessage

object ClientMessage {
  val pickler = compositePickler[ClientMessage]
    .addConcreteType[Subscribe]
    .addConcreteType[CallRequest]
}

object ServerMessage {
  val pickler = compositePickler[ServerMessage]
    .addConcreteType[Response]
    .addConcreteType[Notification]
}
