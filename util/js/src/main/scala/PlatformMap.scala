package wust.util

import scala.scalajs.js

object PlatformMap {
  type Type[T] = js.Dictionary[T]
  @inline def apply[T](): Type[T] = js.Dictionary[T]()
}
