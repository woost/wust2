package wust.webUtil

import scala.scalajs.js

object JSDefined {
  // https://gitter.im/scala-js/scala-js?at=5c3e221135350772cf375515
  def apply[A](a: A): js.UndefOr[A] = a
  def unapply[A](a: js.UndefOr[A]): UnapplyResult[A] = new UnapplyResult(a)

  final class UnapplyResult[+A](val self: js.UndefOr[A])
    extends AnyVal {
    @inline def isEmpty: Boolean = self eq js.undefined
    /** Calling `get` when `isEmpty` is true is undefined behavior. */
    @inline def get: A = self.asInstanceOf[A]
  }
}
