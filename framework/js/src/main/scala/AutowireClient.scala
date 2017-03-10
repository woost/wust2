package wust.framework

import scala.concurrent.Future
import java.nio.ByteBuffer

import boopickle.Default._

class AutowireClient(send: (Seq[String], Map[String, ByteBuffer]) => Future[ByteBuffer]) extends autowire.Client[ByteBuffer, Pickler, Pickler] {
  override def doCall(req: Request): Future[ByteBuffer] = send(req.path, req.args)
  def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
  def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)
}
