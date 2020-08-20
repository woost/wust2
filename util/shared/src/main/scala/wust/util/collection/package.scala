package wust.util

import scala.collection.mutable
import scala.collection.{ AbstractView, BuildFrom }
import scala.collection.generic.IsSeq

package object collection {

  def HashSetFromArray[T](arr: Array[T]): mutable.HashSet[T] = {
    val set = new mutable.HashSet[T]()
    set.sizeHint(arr.length)
    arr.foreach(set += _)
    set
  }


  class CollectionOperations[Repr, T <: IsSeq[Repr]](col: Repr, seq: T) {

    // def findMap[B](f: T => Option[B]): Option[B] = {
    //   col.foreach { x =>
    //     val result = f(x)
    //     if (result.isDefined) return result
    //   }

    //   None
    // }

    // @inline def groupByForeach[K,B](f: ((K, B) => Unit) => T => Unit): scala.collection.Map[K, scala.collection.Seq[B]] = {
    //   val map = mutable.HashMap[K, mutable.ArrayBuffer[B]]()
    //   val add: (K, B) => Unit = { (k,b) =>
    //     val buf = map.getOrElseUpdate(k, mutable.ArrayBuffer[B]())
    //     buf += b
    //     ()
    //   }
    //   col.foreach(f(add))
    //   map
    // }

    // def groupByCollect[K,B](f: PartialFunction[T, (K,B)]): scala.collection.Map[K, scala.collection.Seq[B]] = groupByForeach { add =>
    //   f.runWith { case (k,b) => add(k, b) }.andThen(_ => ())
    // }

    // @inline def groupByMap[K,B](f: T => (K,B)): scala.collection.Map[K, scala.collection.Seq[B]] = groupByForeach { add => t =>
    //   val (a,b) = f(t)
    //   add(a, b)
    // }

    // @inline def by[X](lens: T => X): scala.collection.Map[X, T] = {
    //   val map = mutable.HashMap[X, T]()
    //   map.sizeHint(col.size)
    //   col.foreach { x =>
    //     map(lens(x)) = x
    //   }
    //   map
    // }

    // @inline def histogram[X](lens: T => X = (x:T) => x):scala.collection.Map[X,Long] = {
    //   val map = mutable.HashMap[X, Long]()
    //   col.foreach { x =>
    //     val key = lens(x)
    //     map.update(key, map.getOrElse(key,0L) + 1)
    //   }
    //   map
    // }

    @inline def distinctBy[X, That](lens: seq.A => X)(implicit bf: BuildFrom[Repr, seq.A, That]): That = {
      bf.fromSpecific(col)(seq(col).iterator.filterNot {
        val seen = mutable.HashSet[X]()
        (elem: seq.A) => {
          val id = lens(elem)
          val b = seen(id)
          seen += id
          b
        }
      })
    }

    @inline def foreachWithIndex[U](f: (Int, seq.A) => U): Unit = {
      var counter = 0
      seq(col).foreach { a =>
        val b = f(counter, a)
        counter += 1
        b
      }
    }

    @inline def mapWithIndex[B, That](f: (Int, seq.A) => B)(implicit bf: BuildFrom[Repr, B, That]): That = {
      var counter = 0
      bf.fromSpecific(col)(seq(col).map[B] { a =>
        val b = f(counter, a)
        counter += 1
        b
      }.iterator)
    }

    @inline def flatMapWithIndex[B, That](f: (Int, seq.A) => IterableOnce[B])(implicit bf: BuildFrom[Repr, B, That]): That = {
      var counter = 0
      bf.fromSpecific(col)(seq(col).flatMap[B] { a =>
        val b = f(counter, a)
        counter += 1
        b
      }.iterator)
    }

    private def leftPadTo[That](len: Int, fillElem: seq.A)(implicit bf: BuildFrom[Repr, seq.A, That]): That = {
      val seqOps = seq(col)
      val actualLen = seqOps.size
      val missing = len - actualLen
      if (missing <= 0) bf.fromSpecific(col)(seqOps.iterator)
      else {
        val builder = bf.newBuilder(col)
        builder.sizeHint(len)
        var diff = missing
        while (diff > 0) {
          builder += fillElem
          diff -= 1
        }
        builder ++= seqOps.iterator
        builder.result()
      }
    }
  }


  implicit class CollectionOperationToScalar[A](coll: IterableOnce[A]) {
    def randomSelect: A = {
      coll.iterator.drop(scala.util.Random.nextInt(coll.size)).next
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

  def distinctBuilder[T, That[_]]: DistinctBuilder[T, That[T]] = {
    new DistinctBuilder[T, That[T]](cb.apply())
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
