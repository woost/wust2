package wust.util.collection

import scala.collection.mutable

@inline class BasicJvmSet[Key](inner: mutable.HashSet[Key]) extends BasicSet[Key] {
  @inline def contains(key: Key): Boolean = inner.contains(key)
  @inline def add(key: Key): Unit = inner += key
  @inline def remove(key: Key): Unit = inner -= key
  @inline def clear(): Unit = inner.clear()
  @inline def size: Int = inner.size
  @inline def isEmpty: Boolean = inner.isEmpty
  @inline def foreach[U](f: Key => U): Unit = inner.foreach(f)
}

trait BasicSetNative extends BasicSetFactory {
  @inline def ofString(): BasicSet[String] = apply[String]()
  @inline def ofString(value: String, values: String*): BasicSet[String] = apply[String](value, values)
  @inline def ofString(sizeHint: Int): BasicSet[String] = apply[String](sizeHint)
  @inline def ofInt(): BasicSet[Int] = apply[Int]()
  @inline def ofInt(value: Int, values: Int*): BasicSet[Int] = apply[Int](value, values)
  @inline def ofInt(sizeHint: Int): BasicSet[Int] = apply[Int](sizeHint)

  @inline def apply[Key](): BasicSet[Key] = new BasicJvmSet[Key](new mutable.HashSet)
  def apply[Key](value: Key, values: Seq[Key]): BasicSet[Key] = {
    val set = apply[Key](values.size + 1)
    set.add(value)
    values.foreach(set.add)
    set
  }
  def apply[Key](sizeHint: Int): BasicSet[Key] = {
    val set = new mutable.HashSet[Key]
    set.sizeHint(sizeHint)
    new BasicJvmSet(set)
  }
}
