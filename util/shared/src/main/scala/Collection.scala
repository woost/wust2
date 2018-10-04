package wust.util

import scala.collection.generic.{CanBuildFrom, CanCombineFrom}
import scala.collection.{IterableLike, breakOut, mutable}
import scala.reflect.ClassTag

package object collection {
  implicit class RichCollection[T, Repr[_]](val col: IterableLike[T, Repr[T]]) extends AnyVal {
    def by[X](lens: T => X): scala.collection.Map[X, T] = {
      val map = mutable.HashMap[X, T]()
      map.sizeHint(col.size)
      col.foreach { x =>
        map(lens(x)) = x
      }
      map
    }

    def distinctBy[X](lens: T => X): Repr[T] = col.filterNot {
      val seen = mutable.HashSet[X]()
      elem: T => {
        val id = lens(elem)
        val b = seen(id)
        seen += id
        b
      }
    }

    def topologicalSortBy(next: T => Iterable[T]) = algorithm.topologicalSortSlow(col, next)

    def randomSelect: T = col.iterator.drop(scala.util.Random.nextInt(col.size)).next

    def leftPadTo(len: Int, elem: T)(implicit canBuildFrom: CanBuildFrom[Repr[T], T, Repr[T]]): Repr[T] = {
      leftPadWithBuilder(len, elem, col)
    }
  }

  implicit class RichArray[T <: AnyRef:ClassTag](array:Array[T]) {
    @inline def filterIdx(p: Int => Boolean):Array[T] = {
      val builder = new mutable.ArrayBuilder.ofRef[T]
      var i = 0
      while(i < array.length) {
        if(p(i))
          builder += array(i)
        i += 1
      }
      builder.result()
    }
  }

  implicit class RichIntArray(array:Array[Int]) {
    @inline def filterIdx(p: Int => Boolean): Array[Int] = {
      val builder = new mutable.ArrayBuilder.ofInt
      var i = 0
      while(i < array.length) {
        if(p(i))
          builder += array(i)
        i += 1
      }
      builder.result()
    }
  }

  implicit class RichString(val s: String) extends AnyVal {
    def leftPadTo(len: Int, elem: Char): String = {
      leftPadWithBuilder(len, elem, s)
    }
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
      case _         => Option(a)
    }
  }

  private def leftPadWithBuilder[T, That](len: Int, fillElem: T, elements: IterableLike[T, That])(implicit cb: CanBuildFrom[That, T, That]): That = {
    val actualLen = elements.size
    val missing = len - actualLen
    if (missing <= 0) elements.repr
    else {
      val builder = cb.apply(elements.repr)
      builder.sizeHint(len)
      var diff = missing
      while (diff > 0) {
        builder += fillElem
        diff -= 1
      }
      builder ++= elements
      builder.result()
    }
  }
}
