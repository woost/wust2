package wust.util.collection

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal("Set")
class JsSet[Key] extends js.Object {
  def add(key: Key): Unit = js.native
  def delete(key: Key): Unit = js.native
  def clear(): Unit = js.native
  def has(key: Key): Boolean = js.native
  def forEach(f: js.Function1[Key, Unit]): Unit = js.native
  def values(): js.Iterator[Key] = js.native
  def size: Int = js.native
}

@inline class BasicJsSet[Key](val inner: JsSet[Key]) extends BasicSet[Key] {
  @inline def contains(key: Key): Boolean = inner.has(key)
  @inline def add(key: Key): Unit = inner.add(key)
  @inline def remove(key: Key): Unit = inner.delete(key)
  @inline def clear(): Unit = inner.clear()
  @inline def size: Int = inner.size
  @inline def isEmpty: Boolean = !inner.values().toIterator.hasNext
  @inline def foreach[U](f: Key => U): Unit = inner.forEach(key => f(key))
}

trait BasicSetNative extends BasicSetFactory {
  @inline def ofString(): BasicSet[String] = apply[String]()
  @inline def ofString(value: String, values: String*): BasicSet[String] = apply[String](value, values)
  @inline def ofString(sizeHint: Int): BasicSet[String] = apply[String](sizeHint)
  @inline def ofInt(): BasicSet[Int] = apply[Int]()
  @inline def ofInt(value: Int, values: Int*): BasicSet[Int] = apply[Int](value, values)
  @inline def ofInt(sizeHint: Int): BasicSet[Int] = apply[Int](sizeHint)

  // private because js sets work with reference equality, so it is only applicable for primitives.
  // scala classes normally rely on their equals method and hashcode which is not taken into account
  // in the js world.
  @inline private def apply[Key](): BasicSet[Key] = new BasicJsSet[Key](new JsSet)
  private def apply[Key](value: Key, values: Seq[Key]): BasicSet[Key] = {
    val set = apply[Key]()
    set.add(value)
    values.foreach(set.add)
    set
  }
  @inline private def apply[Key](sizeHint: Int): BasicSet[Key] = apply[Key]()
}
