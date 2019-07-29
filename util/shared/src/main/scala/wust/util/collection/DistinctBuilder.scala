package wust.util

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable

class DistinctBuilder[T, That](builder: mutable.Builder[T, That]) extends mutable.Builder[T, That] {
  private val set = mutable.HashSet[T]()

  @inline def contains(elem: T) = set.contains(elem)

  def +=(elem: T) = {
    if (!contains(elem)) {
      set += elem
      builder += elem
    }
    this
  }

  def clear(): Unit = {
    set.clear()
    builder.clear()
  }

  def result(): That = {
    builder.result()
  }
}
