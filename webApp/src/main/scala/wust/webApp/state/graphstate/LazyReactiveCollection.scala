package wust.webApp.state.graphstate

import scala.reflect.ClassTag
import scala.scalajs.js.JSConverters._
import acyclic.file
import rx._
import flatland._
import wust.ids._
import wust.util.algorithm._
import wust.util.collection._
import wust.util.macros.InlineList
import wust.graph._
import wust.util.time.time
import scala.scalajs.js

import scala.collection.{ breakOut, immutable, mutable }
import scala.scalajs.js.WrappedArray

@inline final class LazyReactiveCollection[T](getCurrent: Int => T) {
  val self: mutable.ArrayBuffer[Var[T]] = mutable.ArrayBuffer.empty

  @inline def length = self.length

  @inline def sizeHint(n:Int) = self.sizeHint(n)

  @inline def grow(): Unit = { self += null }

  @inline def refresh(idx: Int): Unit = {
    if (self(idx) != null) {
      self(idx)() = getCurrent(idx)
    }
  }

  @inline def apply(idx: Int): Var[T] = {
    if (self(idx) == null) {
      val value = Var(getCurrent(idx))
      self(idx) = value
      value
    } else {
      self(idx)
    }
  }
}

