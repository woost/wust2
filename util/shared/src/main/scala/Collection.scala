package wust.util

import scala.collection.{IterableLike, breakOut}

package object collection {
  implicit class RichCollection[T, Repr[T]](val col: IterableLike[T, Repr[T]]) extends AnyVal {
    def by[X](lens: T => X): Map[X, T] = col.map(x => lens(x) -> x)(breakOut)
    def distinctBy[X](lens: T => X) = col.filterNot {
      var set = Set[X]()
      (elem: T) => {
        val id = lens(elem)
        val b = set(id)
        set += id
        b
      }
    }
    def topologicalSortBy(next: T => Iterable[T]) = algorithm.topologicalSort(col, next)
  }

  implicit class RichSet[A](val set: Set[A]) extends AnyVal {
    def toggle(a: A) = if (set(a)) set - a else set + a
  }

  implicit class RichMap[A](val map: Map[A, Boolean]) extends AnyVal {
    def toggle(a: A) = if (map(a)) map.updated(a, false) else map.updated(a, true)
  }

  implicit class RichOption[A](val o: Option[A]) extends AnyVal {
    def setOrToggle(a: A) = o match {
      case Some(`a`) => None
      case _ => Option(a)
    }
  }
}
