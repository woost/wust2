package util

import collection.IterableLike
import collection.breakOut

package object collectionHelpers {
  implicit class RichCollection[T, Repr[T]](col: IterableLike[T, Repr[T]]) {
    def by[X](lens: T => X): Map[X, T] = col.map(x => lens(x) -> x)(breakOut)
    def topologicalSortBy(next: T => Iterable[T]) = algorithm.topologicalSort(col, next)
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
