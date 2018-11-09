package wust.util

import wust.util.collection._

import scala.collection.generic.CanBuildFrom
import scala.reflect.ClassTag

object ArraySet {
  @inline def create(n:Int): ArraySet = new ArraySet(new Array[Int](n))
}

@inline final class ArraySet(val marked:Array[Int]) extends AnyVal {
  //TODO: track number of added nodes to speed up map and allElements
  @inline def add(elem:Int):Unit = marked(elem) = 1
  @inline def add(elems:IndexedSeq[Int]): Unit = elems.foreachElement(add)
  @inline def remove(elem:Int):Unit = marked(elem) = 0
  @inline def remove(elems:IndexedSeq[Int]): Unit =  elems.foreachElement(remove)
  @inline def contains(elem:Int):Boolean = marked(elem) == 1
  @inline def containsNot(elem:Int):Boolean = marked(elem) == 0

  @inline def apply(elem:Int):Boolean = contains(elem)
  @inline def +=(elem:Int):Unit = add(elem)
  @inline def ++=(elems:IndexedSeq[Int]):Unit = add(elems)
  @inline def -=(elem:Int):Unit = remove(elem)
  @inline def --=(elems:IndexedSeq[Int]):Unit = remove(elems)

  @inline def foreach(f:Int => Unit):Unit = {
    marked.foreachIndex{ i =>
      if(contains(i)) f(i)
    }
  }

  @inline def calculateSize: Int = {
    var size = 0
    marked.foreachIndex{ i =>
      if(contains(i)) size += 1
    }
    size
  }

  @inline def mapToArray[T](f:Int => T)(implicit classTag:ClassTag[T]):Array[T] = {
    val array = new Array[T](calculateSize)
    var pos = 0
    foreach{ i =>
      array(pos) = f(i)
      pos += 1
    }
    array
  }

  @inline def map[B, That](f: Int => B)(implicit bf: CanBuildFrom[Array[Int], B, That]): That = {
    val builder = bf(marked)
    builder.sizeHint(calculateSize)

    foreach{ i =>
      builder += f(i)
    }

    builder.result
  }

  @inline def collectAllElements:Array[Int] = {
    val array = new Array[Int](calculateSize)
    var pos = 0
    foreach{ i =>
      array(pos) = i
      pos += 1
    }
    array
  }

  @inline def partition(p:Int => Boolean):(ArraySet,ArraySet) = {
    val trueSet = ArraySet.create(marked.length)
    val falseSet = ArraySet.create(marked.length)
    foreach{ i =>
      if(p(i)) trueSet.add(i)
      else falseSet.add(i)
    }
    (trueSet, falseSet)
  }
}

