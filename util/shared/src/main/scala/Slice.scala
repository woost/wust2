package wust.util

import scala.collection.mutable


final class SliceInt(array: Array[Int], start: Int, val length: Int) extends IndexedSeq[Int] {
  override protected[this] def newBuilder: mutable.Builder[Int, IndexedSeq[Int]] = new mutable.Builder[Int,IndexedSeq[Int]] {
    val self = new mutable.ArrayBuilder.ofInt
    override def +=(elem: Int): this.type = {self += elem; this}
    override def clear(): Unit = self.clear()
    override def result(): IndexedSeq[Int] = self.result()
    override def sizeHint(size: Int): Unit = self.sizeHint(size)
  }

  @inline override def apply(idx: Int): Int = array(start + idx)
  override def iterator: Iterator[Int] = array.iterator.slice(start, start + length)
  @inline override def slice(from: Int, until: Int): SliceInt = new SliceInt(array, start + from, until - from)
}

object SliceInt {
  def fromArray(array:Array[Int]):SliceInt = {
    new SliceInt(array, 0, array.length)
  }
}
