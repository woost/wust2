package wust.util.collection

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.|

@js.native
@JSGlobal("Map")
class JsMap[Key, Value] extends js.Object {
  def set(key: Key, value: Value): Unit = js.native
  def delete(key: Key): Unit = js.native
  def clear(): Unit = js.native
  def get(key: Key): js.UndefOr[Value] = js.native
  def has(key: Key): Boolean = js.native
  def keys(): js.Iterator[Key] = js.native
  def values(): js.Iterator[Value] = js.native
  def entries(): js.Iterator[js.Array[Key | Value]] = js.native
  def forEach(f: js.Function2[Value, Key, Unit]): Unit = js.native
  def size: Int = js.native
}

@inline class BasicJsMap[Key, Value](val inner: JsMap[Key, Value]) extends BasicMap[Key, Value] {
  @inline def isDefinedAt(key: Key): Boolean = inner.has(key)
  @inline def get(key: Key): Option[Value] = inner.get(key).toOption
  @inline def apply(key: Key): Value = inner.get(key).get
  @inline def update(key: Key, value: Value): Unit = inner.set(key, value)
  @inline def remove(key: Key): Unit = inner.delete(key)
  @inline def clear(): Unit = inner.clear()
  @inline def values: Iterator[Value] = inner.values().toIterator
  @inline def size: Int = inner.size
  @inline def isEmpty: Boolean = !inner.values().toIterator.hasNext
  @inline def foreach[U](f: (Key, Value) => U): Unit = inner.forEach((value, key) => f(key, value))
  @inline def keys: Iterator[Key] = inner.keys().toIterator
}

trait BasicMapNative extends BasicMapFactory {
  @inline def ofString[Value](): BasicMap[String, Value] = apply[String, Value]()
  @inline def ofString[Value](value: (String, Value), values: (String, Value)*): BasicMap[String, Value] = apply[String, Value](value, values)
  @inline def ofString[Value](sizeHint: Int): BasicMap[String, Value] = apply[String, Value](sizeHint)
  @inline def ofInt[Value](): BasicMap[Int, Value] = apply[Int, Value]()
  @inline def ofInt[Value](value: (Int, Value), values: (Int, Value)*): BasicMap[Int, Value] = apply[Int, Value](value, values)
  @inline def ofInt[Value](sizeHint: Int): BasicMap[Int, Value] = apply[Int, Value](sizeHint)

  // private because js maps work with reference equality, so it is only applicable for primitives.
  // scala classes normally rely on their equals method and hashcode which is not taken into account
  // in the js world.
  @inline private def apply[Key, Value](): BasicMap[Key, Value] = new BasicJsMap[Key, Value](new JsMap)
  private def apply[Key, Value](value: (Key, Value), values: Seq[(Key, Value)]): BasicMap[Key, Value] = {
    val map = apply[Key, Value]()
    map += value
    values.foreach(map += _)
    map
  }
  @inline private def apply[Key, Value](sizeHint: Int): BasicMap[Key, Value] = apply[Key, Value]()
}
