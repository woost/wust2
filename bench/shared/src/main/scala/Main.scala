package bench

import scala.concurrent.duration._
import Util._

object Main {
  def main(args: Array[String]): Unit = {
    // runComparison(Benchmarks.linearScan, expRange(1000), 1000, 30 seconds)
    runComparison(GraphBenchmarks.graphAlgorithms, expRange(1000), 1000, 60 minutes)
  }
}

