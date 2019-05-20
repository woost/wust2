package wust.bench

import scala.concurrent.duration._
import bench._
import bench.util._
import scribe.Logger

object Main {
  def main(args: Array[String]): Unit = {
    Logger.root.clearHandlers().replace()


    runComparison(DepthFirstSearch.comparison, expRange(4000, 8), 1 minutes)
    // runComparison(GraphBenchmarks.graphAlgorithms, expRange(1000, 4), 2 minutes)
    // runComparison(GraphBenchmarks.topologicalSort, expRange(1000, 4), 2 minutes)
    // runComparison(CuidBenchmarks.serialization, List(10000), 3 minutes)
  }
}
