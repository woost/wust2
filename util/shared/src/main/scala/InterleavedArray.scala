package wust.util

object InterleavedArray {
  @inline def create(n:Int): InterleavedArray = new InterleavedArray(new Array[Int](n*2))
}

@inline final class InterleavedArray(val interleaved:Array[Int]) extends AnyVal {
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

