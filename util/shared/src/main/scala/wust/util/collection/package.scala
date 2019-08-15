package wust.util

import scala.collection.generic.CanBuildFrom
import scala.collection.{GenTraversableOnce, IterableLike, mutable}

package object collection {

  def HashSetFromArray[T](arr: Array[T]): mutable.HashSet[T] = {
    val set = new mutable.HashSet[T]()
    set.sizeHint(arr.length)
    arr.foreach(set += _)
    set
  }


  implicit class RichCollection[T, Repr[_]](val col: IterableLike[T, Repr[T]]) extends AnyVal {

    @inline def groupByForeach[K,B](f: ((K, B) => Unit) => T => Unit): scala.collection.Map[K, scala.collection.Seq[B]] = {
      val map = mutable.HashMap[K, mutable.ArrayBuffer[B]]()
      val add: (K, B) => Unit = { (k,b) =>
        val buf = map.getOrElseUpdate(k, mutable.ArrayBuffer[B]())
        buf += b
        ()
      }
      col.foreach(f(add))
      map
    }

    @inline def groupByCollect[K,B](f: PartialFunction[T, (K,B)]): scala.collection.Map[K, scala.collection.Seq[B]] = groupByForeach { add =>
      f.runWith { case (k,b) => add(k, b) }.andThen(_ => ())
    }

    @inline def groupByMap[K,B](f: T => (K,B)): scala.collection.Map[K, scala.collection.Seq[B]] = groupByForeach { add => t =>
      val (a,b) = f(t)
      add(a, b)
    }

    @inline def by[X](lens: T => X): scala.collection.Map[X, T] = {
      val map = mutable.HashMap[X, T]()
      map.sizeHint(col.size)
      col.foreach { x =>
        map(lens(x)) = x
      }
      map
    }

    @inline def histogram[X](lens: T => X = (x:T) => x):scala.collection.Map[X,Long] = {
      val map = mutable.HashMap[X, Long]()
      col.foreach { x =>
        val key = lens(x)
        map.update(key, map.getOrElse(key,0L) + 1)
      }
      map
    }

    @inline def distinctBy[X](lens: T => X): Repr[T] = col.filterNot {
      val seen = mutable.HashSet[X]()
      (elem: T) => {
        val id = lens(elem)
        val b = seen(id)
        seen += id
        b
      }
    }

    @inline def foreachWithIndex[U](f: (Int, T) => U): Unit = {
      var counter = 0
      col.foreach { a =>
        val b = f(counter, a)
        counter += 1
        b
      }
    }

    @inline def mapWithIndex[B, That](f: (Int, T) => B)(implicit bf: CanBuildFrom[Repr[T], B, That]): That = {
      var counter = 0
      col.map[B, That] { a =>
        val b = f(counter, a)
        counter += 1
        b
      }
    }

    @inline def flatMapWithIndex[B, That](f: (Int, T) => GenTraversableOnce[B])(implicit bf: CanBuildFrom[Repr[T], B, That]): That = {
      var counter = 0
      col.flatMap[B, That] { a =>
        val b = f(counter, a)
        counter += 1
        b
      }
    }

    def randomSelect: T = col.iterator.drop(scala.util.Random.nextInt(col.size)).next

    def leftPadTo(len: Int, elem: T)(implicit canBuildFrom: CanBuildFrom[Repr[T], T, Repr[T]]): Repr[T] = {
      leftPadWithBuilder(len, elem, col)
    }
  }

  implicit class RichString(val s: String) extends AnyVal {
    def leftPadTo(len: Int, elem: Char): String = {
      leftPadWithBuilder(len, elem, s)
    }
  }

  implicit class RichSeqOps[A](val sequence: Seq[A]) extends AnyVal {
    @inline def viewMap[B](f: A => B): MappedSeq[A, B] = new MappedSeq[A,B](sequence, f)
  }
  implicit class RichIndexedSeqOps[A](val sequence: IndexedSeq[A]) extends AnyVal {
    @inline def viewMap[B](f: A => B): MappedIndexedSeq[A, B] = new MappedIndexedSeq[A,B](sequence, f)
  }
  implicit class RichArrayOps[A](val array: Array[A]) extends AnyVal {
    @inline def viewMap[B](f: A => B): MappedArray[A, B] = new MappedArray[A,B](array, f)
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

  def groupByBuilder[K,T]: GroupByBuilder[K,T] = new GroupByBuilder[K,T]

  def distinctBuilder[T, That[_]](implicit cb: CanBuildFrom[That[T], T, That[T]]): DistinctBuilder[T, That[T]] = {
    new DistinctBuilder[T, That[T]](cb.apply())
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

  def eitherSeq[A, B](list: Seq[Either[A, B]]): Either[Seq[A], Seq[B]] = {
    val lefts = new mutable.ArrayBuffer[A]
    val rights = new mutable.ArrayBuffer[B]

    list.foreach {
      case Right(r) => rights += r
      case Left(l) => lefts += l
    }

    if (lefts.isEmpty) Right(rights.result) else Left(lefts.result)
  }
}
