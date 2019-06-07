package wust.util.collection

import scala.collection.mutable

@inline class BasicJvmMap[Key, Value](inner: mutable.HashMap[Key, Value]) extends BasicMap[Key, Value] {
  @inline def isDefinedAt(key: Key): Boolean = inner.isDefinedAt(key)
  @inline def get(key: Key): Option[Value] = inner.get(key)
  @inline def apply(key: Key): Value = inner(key)
  @inline def update(key: Key, value: Value): Unit = inner(key) = value
  @inline def remove(key: Key): Unit = inner -= key
  @inline def clear(): Unit = inner.clear()
  @inline def values: Iterator[Value] = inner.valuesIterator
  @inline def size: Int = inner.size
  @inline def isEmpty: Boolean = inner.isEmpty
  @inline def foreach[U](f: (Key, Value) => U): Unit = inner.foreach { case (key, value) => f(key, value) }
  @inline def keys: Iterator[Key] = inner.keysIterator
}

trait BasicMapNative extends BasicMapFactory {
  @inline def ofString[Value](): BasicMap[String, Value] = apply[String, Value]()
  @inline def ofString[Value](value: (String, Value), values: (String, Value)*): BasicMap[String, Value] = apply[String, Value](value, values)
  @inline def ofString[Value](sizeHint: Int): BasicMap[String, Value] = apply[String, Value](sizeHint)
  @inline def ofInt[Value](): BasicMap[Int, Value] = apply[Int, Value]()
  @inline def ofInt[Value](value: (Int, Value), values: (Int, Value)*): BasicMap[Int, Value] = apply[Int, Value](value, values)
  @inline def ofInt[Value](sizeHint: Int): BasicMap[Int, Value] = apply[Int, Value](sizeHint)

  @inline def apply[Key, Value](): BasicMap[Key, Value] = new BasicJvmMap[Key, Value](new mutable.HashMap)
  @inline def apply[Key, Value](value: (Key, Value), values: Seq[(Key, Value)]): BasicMap[Key, Value] = {
    val map = apply[Key, Value](values.size + 1)
    map += value
    values.foreach(map += _)
    map
  }
  @inline def apply[Key, Value](sizeHint: Int): BasicMap[Key, Value] = {
    val map = new mutable.HashMap[Key, Value]
    map.sizeHint(sizeHint)
    new BasicJvmMap(map)
  }
}
