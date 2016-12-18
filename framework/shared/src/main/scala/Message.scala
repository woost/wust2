package framework.message

import java.nio.ByteBuffer

//TODO: fix double serialization of messages

sealed trait ClientMessage
case class NotifyRequest(string: String) extends ClientMessage
case class CallRequest(seqId: Int, path: Seq[String], args: Map[String, ByteBuffer]) extends ClientMessage

sealed trait ServerMessage
case class Response(seqId: Int, result: ByteBuffer) extends ServerMessage
case class Notification(result: ByteBuffer) extends ServerMessage
