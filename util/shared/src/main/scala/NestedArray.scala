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
  @inline def get(idx1: Int, idx2:Int): Option[Int] = if(idx1 < length && idx2 < sliceLength(idx1)) Some(apply(idx1,idx2)) else None
  @inline def update(idx1: Int, idx2:Int, newValue:Int): Unit = data(sliceStart(idx1)+idx2) = newValue
  @inline def foreachElement(idx: Int)(f:Int => Unit):Unit = {
    var i = 0
    val n = sliceLength(idx)
    while(i < n) {
      f(apply(idx,i))
      i += 1
    }
  }
}

final class NestedArrayIntBuilder(nestedArray: NestedArrayInt){
  var filled = new Array[Int](nestedArray.length)
  def add(idx:Int, value:Int):Unit = {
    // assert(idx < nestedArray.length)
    // assert(filled(idx) < nestedArray.sliceLength(idx), idx)
    nestedArray.update(idx, filled(idx), value)
    filled(idx) += 1
  }

  def result():NestedArrayInt = {
    filled = null
    nestedArray
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

  def apply(sliceLengths:Array[Int]):NestedArrayInt = {
    val sliceArray = InterleavedArray.create(sliceLengths.length)
    var currentStart = 0
    sliceLengths.foreachIndexAndElement{ (i,sliceLength) =>
      sliceArray.updatea(i, currentStart)
      sliceArray.updateb(i, sliceLength)
      currentStart += sliceLength
    }
    val array = new Array[Int](currentStart)
    new NestedArrayInt(array, sliceArray)
  }

  def builder(sliceLengths:Array[Int]):NestedArrayIntBuilder = {
    new NestedArrayIntBuilder(apply(sliceLengths))//, sliceLengths)
  }

  def apply(nested:Array[mutable.ArrayBuilder.ofInt]): NestedArrayInt = {
    // ArrayBuilders can also be null to represent an empty builder 
    var currentStart = 0
    val sliceArray = InterleavedArray.create(nested.length)
    val builtSlices = new Array[Array[Int]](nested.length)

    nested.foreachIndexAndElement{(i, sliceBuilder) =>
      if(sliceBuilder != null) {
        val slice = sliceBuilder.result()
        builtSlices(i) = slice
        sliceArray.updatea(i,currentStart)
        sliceArray.updateb(i,slice.length)

        currentStart += slice.length
      } else {
        // empty builder => empty slice
        builtSlices(i) = null
        sliceArray.updatea(i,currentStart)
        sliceArray.updateb(i,0)
      }
    }

    val array = new Array[Int](currentStart)
    currentStart = 0
    builtSlices.foreachIndexAndElement{ (i, slice) =>
      if(slice != null) {
        slice.copyToArray(array, currentStart)
        currentStart += slice.length
      }
    }

    new NestedArrayInt(array, sliceArray)
  }
}

