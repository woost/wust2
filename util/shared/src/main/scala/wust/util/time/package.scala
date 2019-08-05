package wust.util

package object time {
  @inline def time[T](name: String)(code: => T): T = {
    val start = System.nanoTime
    val result: T = code
    val duration = (System.nanoTime - start) / 1000000.0
    scribe.info(s"$name: ${ duration }ms")
    result
  }
}


