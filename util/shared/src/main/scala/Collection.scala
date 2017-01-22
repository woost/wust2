package util

import collection.IterableLike
import collection.breakOut

package object collectionHelpers {
  implicit class RichCollection[T, Repr](col: IterableLike[T, Repr]) {
    def by[X](lens: T => X): Map[X, T] = col.map(x => lens(x) -> x)(breakOut)
  }
}
