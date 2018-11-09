package wust.util

object ArrayStackInt {
  @inline def create(capacity:Int) = {
    val data = new Array[Int](1 + capacity)
    data(0) = 1 // initialize the top-pointer
    new ArrayStackInt(data)
  }
}

final class ArrayStackInt private (private val data:Array[Int]) extends AnyVal {
  @inline private def top = data(0)
  @inline private def incrementTop(): Unit = data(0) += 1
  @inline private def decrementTop(): Unit = data(0) -= 1

  @inline def isEmpty = top == 1
  @inline def push(value:Int) = {
    data(top) = value
    incrementTop()
  }
  @inline def pop() = {
    decrementTop()
    data(top)
  }
}

