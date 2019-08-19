package wust.util

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable

class GroupByBuilder[K, T] extends mutable.Builder[(K, T), scala.collection.Map[K, scala.collection.Seq[T]]] {
  val map = mutable.HashMap[K, mutable.ArrayBuffer[T]]()

  def +=(elem: (K,T)) = {
    val (k,t) = elem
    val buf = map.getOrElseUpdate(k, mutable.ArrayBuffer[T]())
    buf += t
    this
  }

  def clear(): Unit = {
    map.clear()
  }

  def result(): scala.collection.Map[K, scala.collection.Seq[T]] = map
}
