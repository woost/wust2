package wust.util

import scala.collection.mutable

object PlatformMap {
  type Type[T] = mutable.HashMap[String, T]
  @inline def apply[T](): Type[T] = new mutable.HashMap[String, T]()
}
