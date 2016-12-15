package api

import java.nio.ByteBuffer

trait Api {
  def change(delta: Int): Int
}

sealed trait WebsocketMessage
case class Call(seqId: Int, path: Seq[String], args: Map[String, ByteBuffer]) extends WebsocketMessage
case class Response(seqId: Int, result: ByteBuffer) extends WebsocketMessage

trait EventType
sealed trait WebSocketEvent extends WebsocketMessage with EventType
case class SetCounter(newValue: Int) extends WebsocketMessage with WebSocketEvent
