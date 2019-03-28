package wust.bench

import scala.concurrent.duration._
import bench._
import bench.util._

object Main {
  def main(args: Array[String]): Unit = {
    runComparison(GraphBenchmarks.algorithms, expRange(1000, 4), 1000, 2 minutes)
    runComparison(CuidBenchmarks.serialization, List(10000), 1, 3 minutes)
    runComparison(GraphBenchmarks.graphAlgorithms, expRange(1000), 1000, 60 minutes)
  }
}
