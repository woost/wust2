package wust.util.collection

trait BasicSet[Key] {
  @inline def contains(key: Key): Boolean
  @inline def add(key: Key): Unit
  @inline def remove(key: Key): Unit
  @inline def foreach[U](f: Key => U): Unit
  @inline def clear(): Unit
  @inline def size: Int
  @inline def isEmpty: Boolean

  @inline def apply(key: Key): Boolean = contains(key)
  @inline def +=(key: Key): Unit = add(key)
  @inline def -=(key: Key): Unit = remove(key)
}

trait BasicSetFactory {
  @inline def ofString(): BasicSet[String]
  @inline def ofString(value: String, values: String*): BasicSet[String]
  @inline def ofString(sizeHint: Int): BasicSet[String]
  @inline def ofInt(): BasicSet[Int]
  @inline def ofInt(value: Int, values: Int*): BasicSet[Int]
  @inline def ofInt(sizeHint: Int): BasicSet[Int]
}
object BasicSet extends BasicSetNative with BasicSetFactory
