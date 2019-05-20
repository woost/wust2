package wust.util.collection

@inline class MappedSeq[T, R](sequence: Seq[T], f: T => R) extends Seq[R] {
  override def iterator: Iterator[R] = new Iterator[R] {
    private val inner: Iterator[T] = sequence.toIterator
    override def hasNext: Boolean = inner.hasNext
    override def next(): R = f(inner.next())
  }
  @inline override def length: Int = sequence.length
  @inline override def apply(idx: Int): R = f(sequence(idx))
}
@inline class MappedIndexedSeq[T, R](sequence: IndexedSeq[T], f: T => R) extends IndexedSeq[R] {
  @inline override def length: Int = sequence.length
  @inline override def apply(idx: Int): R = f(sequence(idx))
}
@inline class MappedArray[T, R](seq: Array[T], f: T => R) extends IndexedSeq[R] {
  @inline override def length: Int = seq.length
  @inline override def apply(idx: Int): R = f(seq(idx))
}
