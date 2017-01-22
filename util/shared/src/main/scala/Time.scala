package util
package object time {
  def time[T](name: String)(code: => T): T = {
    val start = System.nanoTime
    val result: T = code
    val duration = (System.nanoTime - start) / 1000000
    println(s"$name: ${duration}ms")
    result
  }
}
