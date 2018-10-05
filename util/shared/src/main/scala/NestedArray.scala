package wust.util

import wust.util.collection.InterleavedArray
import wust.util.collection._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuilder

final class NestedArrayInt(data: Array[Int], sliceArray: InterleavedArray) extends IndexedSeq[SliceInt] {
  // sliceArray stores start/length of nested array interleaved

  @inline def sliceStart(idx: Int):Int = sliceArray.a(idx)
  @inline def sliceLength(idx: Int):Int = sliceArray.b(idx)
  @inline def sliceIsEmpty(idx: Int):Boolean = sliceLength(idx) == 0
  @inline def sliceNonEmpty(idx: Int):Boolean = sliceLength(idx) > 0
  @inline override def length: Int = sliceArray.elementCount
  @inline override def apply(idx: Int): SliceInt = new SliceInt(data, sliceStart(idx), sliceLength(idx))
  @inline def apply(idx1: Int, idx2:Int): Int = data(sliceStart(idx1)+idx2)
  @inline def foreachElement(idx: Int)(f:Int => Unit):Unit = {
    var i = 0
    val n = sliceLength(idx)
    while(i < n) {
      f(apply(idx,i))
      i += 1
    }
  }
}

object NestedArrayInt {
  def apply(nested:Array[Array[Int]]): NestedArrayInt = {
    var currentStart = 0
    val sliceArray = InterleavedArray.create(nested.length)
    nested.foreachIndexAndElement { (i, slice) =>
      sliceArray.updatea(i,currentStart)
      sliceArray.updateb(i,slice.length)

      currentStart += slice.length
    }

    val data = new Array[Int](currentStart)
    currentStart = 0
    nested.foreachIndexAndElement { (i, slice) =>
      slice.copyToArray(data, currentStart)
      currentStart += slice.length
    }

    new NestedArrayInt(data, sliceArray)
  }

  def apply(nested:Array[mutable.ArrayBuilder.ofInt]): NestedArrayInt = {
    var currentStart = 0
    val sliceArray = InterleavedArray.create(nested.length)
    val builtSlices = new Array[Array[Int]](nested.length)

    nested.foreachIndexAndElement{(i, sliceBuilder) =>
      val slice = sliceBuilder.result()
      builtSlices(i) = slice
      sliceArray.updatea(i,currentStart)
      sliceArray.updateb(i,slice.length)

      currentStart += slice.length
    }

    val array = new Array[Int](currentStart)
    currentStart = 0
    builtSlices.foreachIndexAndElement{ (i, slice) =>
      slice.copyToArray(array, currentStart)
      currentStart += slice.length
    }

    new NestedArrayInt(array, sliceArray)
  }
}

