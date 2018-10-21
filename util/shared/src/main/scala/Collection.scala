package wust.util

import scala.collection.generic.{CanBuildFrom, CanCombineFrom}
import scala.collection.{IterableLike, breakOut, mutable}
import scala.reflect.ClassTag
import supertagged._

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


  implicit final class RichIndexedSeq[T](val self:IndexedSeq[T]) extends AnyVal {
    @inline def minMax(smallerThan: (T, T) => Boolean): (T, T) = {
      if (self.isEmpty) throw new UnsupportedOperationException("minMax on empty sequence")

      var min: T = self(0)
      var max: T = min

      var i = 1
      while (i < self.length) {
        val value = self(i)
        if (smallerThan(value, min)) min = value
        if (smallerThan(max, value)) max = value
        i += 1
      }

      (min, max)
    }

    //    @inline def filterIdx(p: Int => Boolean)(implicit ev: ClassTag[T]):Array[T] = {
    //      val builder = new mutable.ArrayBuilder.ofRef[T]
    //      var i = 0
    //      while(i < array.length) {
    //        if(p(i))
    //          builder += array(i)
    //        i += 1
    //      }
    //      builder.result()
    //    }

    @inline def foreachIndex(f: Int => Unit): Unit = {
      val n = self.length
      var i = 0

      while(i < n ) {
        f(i)
        i += 1
      }
    }

    @inline def foreachElement(f: T => Unit): Unit = {
      val n = self.length
      var i = 0

      while(i < n ) {
        f(self(i))
        i += 1
      }
    }

    @inline def foreachIndexAndElement(f: (Int,T) => Unit): Unit = {
      val n = self.length
      var i = 0

      while(i < n ) {
        f(i, self(i))
        i += 1
      }
    }
  }

  implicit final class RichArray[T](val array:Array[T]) extends AnyVal {
    @inline def filterIdx(p: Int => Boolean)(implicit ev: ClassTag[T]):Array[T] = {
      val builder = mutable.ArrayBuilder.make[T]
      var i = 0
      while(i < array.length) {
        if(p(i))
          builder += array(i)
        i += 1
      }
      builder.result()
    }

    @inline def foreachIndex(f: Int => Unit): Unit = {
      val n = array.length
      var i = 0

      while(i < n ) {
        f(i)
        i += 1
      }
    }

    @inline def foreachElement(f: T => Unit): Unit = {
      val n = array.length
      var i = 0

      while(i < n ) {
        f(array(i))
        i += 1
      }
    }

    @inline def foreachIndexAndElement(f: (Int,T) => Unit): Unit = {
      val n = array.length
      var i = 0

      while(i < n ) {
        f(i, array(i))
        i += 1
      }
    }
  }

  implicit final class RichIntArray(val array:Array[Int]) extends AnyVal {
    @inline def filterIndex(p: Int => Boolean): Array[Int] = {
      val builder = new mutable.ArrayBuilder.ofInt
      var i = 0
      while(i < array.length) {
        if(p(i))
          builder += array(i)
        i += 1
      }
      builder.result()
    }

    @inline def markerArray(n:Int):ArraySet = {
      val marked = ArraySet.create(n)
      marked.add(array)
      marked
    }
  }

  object InterleavedArray extends TaggedType[Array[Int]] {
    @inline def create(n:Int): InterleavedArray = apply(new Array[Int](n*2))
  }
  type InterleavedArray = InterleavedArray.Type

  object ArraySet extends TaggedType[Array[Int]] {
    @inline def create(n:Int): ArraySet = apply(new Array[Int](n))
  }
  type ArraySet = ArraySet.Type

  implicit final class RichInterleavedArray(val interleaved:InterleavedArray) extends AnyVal {
    @inline def a(i:Int): Int = interleaved(i*2)
    @inline def b(i:Int): Int = interleaved(i*2+1)
    @inline def updatea(i:Int, value:Int): Unit = interleaved(i*2) = value
    @inline def updateb(i:Int, value:Int): Unit = interleaved(i*2+1) = value
    @inline def elementCount:Int = interleaved.length / 2

    @inline def foreachTwoElements(f: (Int,Int) => Unit): Unit = {
      val n = elementCount
      var i = 0

      while(i < n ) {
        f(a(i), b(i))
        i += 1
      }
    }
    @inline def foreachIndexAndTwoElements(f: (Int,Int,Int) => Unit): Unit = {
      val n = elementCount
      var i = 0

      while(i < n ) {
        f(i, a(i), b(i))
        i += 1
      }
    }
  }

  implicit final class RichArraySet(val marked:ArraySet) extends AnyVal {
    @inline def add(indices:IndexedSeq[Int]): Unit = {
      indices.foreachElement{ index =>
        marked(index) = 1
      }
    }
    @inline def remove(indices:IndexedSeq[Int]): Unit = {
      indices.foreachElement{ index =>
        marked(index) = 0
      }
    }

    @inline def add(i:Int):Unit = marked(i) = 1
    @inline def remove(i:Int):Unit = marked(i) = 0
    @inline def contains(i:Int):Boolean = marked(i) == 1
    @inline def containsNot(i:Int):Boolean = marked(i) == 0

    @inline def foreachAdded(f:Int => Unit):Unit = {
      marked.foreachIndex{ i =>
        if(contains(i)) f(i)
      }
    }

    @inline def map[T](f:Int => T)(implicit classTag:ClassTag[T]):Array[T] = {
      val builder = mutable.ArrayBuilder.make[T]
      foreachAdded{ i =>
        builder += f(i)
      }
      builder.result()
    }

    @inline def allElements:Array[Int] = {
      val builder = new mutable.ArrayBuilder.ofInt
      foreachAdded{ i =>
        builder += i
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
