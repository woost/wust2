package wust.framework

import akka.http.scaladsl.model.ws.BinaryMessage
import akka.util.ByteString
import boopickle.Default._

object WebsocketSerializer {
  def serialize[T: Pickler](msg: T): BinaryMessage.Strict = {
    val bytes = Pickle.intoBytes(msg)
    BinaryMessage(ByteString(bytes))
  }

  def deserialize[T: Pickler](bm: BinaryMessage.Strict): T = {
    val bytes = bm.getStrictData.asByteBuffer
    val msg = Unpickle[T].fromBytes(bytes)
    msg
  }
}
