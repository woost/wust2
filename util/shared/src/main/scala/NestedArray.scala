package wust.util

import scala.collection.mutable

final class NestedArrayInt(array: Array[Int], sliceArray: Array[Int]) extends IndexedSeq[SliceInt] {
  // sliceArray stores start/length of nested array interleaved
  
  @inline def sliceStart(idx: Int):Int = sliceArray(idx*2)
  @inline def sliceLength(idx: Int):Int = sliceArray(idx*2+1)
  @inline def sliceIsEmpty(idx: Int):Boolean = sliceLength(idx) == 0
  @inline def sliceNonEmpty(idx: Int):Boolean = sliceLength(idx) > 0
  override def length: Int = sliceArray.length / 2
  override def apply(idx: Int): SliceInt = new SliceInt(array, sliceStart(idx), sliceLength(idx))
  @inline def apply(idx1: Int, idx2:Int): Int = array(sliceStart(idx1)+idx2)
}

object NestedArrayInt {
  def apply(nested:Array[Array[Int]]): NestedArrayInt = {
    var currentStart = 0
    val sliceArray = new Array[Int](nested.length * 2)
    var i = 0
    while( i < nested.length ) {
      val slice = nested(i)
      sliceArray(i*2+0) = currentStart
      sliceArray(i*2+1) = slice.length

      currentStart += slice.length

      i += 1
    }

    val array = new Array[Int](currentStart)
    currentStart = 0
    i = 0
    while(i < nested.length) {
      val slice = nested(i)
      slice.copyToArray(array, currentStart)
      currentStart += slice.length
      i += 1
    }

    new NestedArrayInt(array, sliceArray)
  }

  def apply(nested:Array[mutable.ArrayBuilder.ofInt]): NestedArrayInt = {
    var currentStart = 0
    val sliceArray = new Array[Int](nested.length * 2)
    val builtSlices = new Array[Array[Int]](nested.length)
    var i = 0
    while( i < nested.length ) {
      val slice = nested(i).result()
      builtSlices(i) = slice
      sliceArray(i*2+0) = currentStart
      sliceArray(i*2+1) = slice.length

      currentStart += slice.length

      i += 1
    }

    val array = new Array[Int](currentStart)
    currentStart = 0
    i = 0
    while(i < nested.length) {
      val slice = builtSlices(i)
      slice.copyToArray(array, currentStart)
      currentStart += slice.length
      i += 1
    }

    new NestedArrayInt(array, sliceArray)
  }
}

