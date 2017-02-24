package util

import collection.IterableLike
import collection.breakOut

package object collectionHelpers {
  implicit class RichCollection[T, Repr](col: IterableLike[T, Repr]) {
    def by[X](lens: T => X): Map[X, T] = col.map(x => lens(x) -> x)(breakOut)
  }

  implicit class RichSet[A](set: Set[A]) {
    def toggle(a: A) = if (set(a)) set - a else set + a
  }

  implicit class RichOption[A](o: Option[A]) {
    def setOrToggle(a: A) = o match {
      case Some(`a`) => None
      case _ => Some(a)
    }
  }
}
