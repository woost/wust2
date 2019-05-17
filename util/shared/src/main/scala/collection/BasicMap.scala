package wust.util.collection

import scala.collection.mutable

trait BasicMap[Key, Value] {
  @inline def isDefinedAt(key: Key): Boolean
  @inline def get(key: Key): Option[Value]
  @inline def apply(key: Key): Value
  @inline def update(key: Key, value: Value): Unit
  @inline def foreach[U](f: (Key, Value) => U): Unit
  @inline def keys: Iterator[Key]
  @inline def delete(key: Key): Unit
  @inline def clear(): Unit
  @inline def size: Int
  @inline def values: Iterator[Value]
  @inline def isEmpty: Boolean

  @inline def getOrElse(key: Key, value: => Value): Value = get(key).getOrElse(value)
  @inline def +=(kv: (Key, Value)): Unit = update(kv._1, kv._2)
  @inline def -=(key: Key): Unit = delete(key)
}

trait BasicMapFactory {
  @inline def ofString[Value](): BasicMap[String, Value]
  @inline def ofString[Value](sizeHint: Int): BasicMap[String, Value]
  @inline def ofInt[Value](): BasicMap[Int, Value]
  @inline def ofInt[Value](sizeHint: Int): BasicMap[Int, Value]
}
object BasicMap extends BasicMapNative with BasicMapFactory
