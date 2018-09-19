package bench

import scala.concurrent.duration._
import Util._

case class Benchmark[T](name: String, init: Int => T, code: (T, Long) => Any) {
  def run(size: Int, iterations: Long) = code(init(size), iterations)
}

case class Comparison(name: String, benchmarks: Seq[Benchmark[_]])
