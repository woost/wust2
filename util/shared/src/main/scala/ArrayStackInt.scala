package wust.util

final class ArrayStackInt(capacity:Int) {
  private val data = new Array[Int](capacity)
  var top = 0
  @inline final def isEmpty = top == 0
  @inline final def push(value:Int) = {
    data(top) = value
    top += 1
  }
  @inline final def pop() = {
    top -= 1
    data(top)
  }
}

