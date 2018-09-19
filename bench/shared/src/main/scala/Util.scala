package bench

import scala.concurrent.duration._

object Util {
  @inline def rInt: Int = util.Random.nextInt
  @inline def rDouble: Double = util.Random.nextDouble
  @inline def loop[T](n: Long)(code: => T): T = {
    var last: T = code
    var i = 1
    while(i < n) {
      last = code
      i += 1
    }
    last
  }

  @inline def whilei(n: Int)(code: Int => Any): Unit = {
    var i = 0
    while(i < n) {
      code(i)
      i += 1
    }
  }

  def expRange(max: Int) = {
    require(max <= (1 << 30))
    List.tabulate[Int]((Math.log(max)/Math.log(2)).ceil.toInt + 1)(i => 1 << i)
  }

  @inline def now: Long = System.nanoTime()

  def runFor[T](duration: Duration)(code: => T): Duration = {
    val durationNs = duration.toNanos
    val start = now

    @inline def passed = now - start

    var count: Long = 0
    while(passed < durationNs) {
      code
      count += 1
    }
    val end = now
    if(count <= 10) println(s"WARNING: only $count iterations done. Give me more time.")
    val total = end - start
    val avg = Duration.fromNanos(total.toDouble / count)
    avg
  }

  def runBenchmark(benchmark: Benchmark[_], size: Int, iterations: Long, duration: Duration): Duration = {
    val onlyInit: Duration = runFor(duration / 2) {
      benchmark.init(size)
    }
    val initAndCode: Duration = runFor(duration / 2) {
      benchmark.run(size, iterations)
    }
    initAndCode - onlyInit
  }

  val defaultWarmup = 2
  def benchmarkSeries(benchmark: Benchmark[_], sizes: Seq[Int], iterations: Long, duration: Duration, warmup: Int = defaultWarmup):Seq[(Int,Duration)] = {
    val seriesDuration = duration / (warmup + 1) // keep only one result
    def runSeries = {
      sizes.map { size =>
        size -> runBenchmark(benchmark, size, iterations, seriesDuration / sizes.size)
      }
    }
    loop(warmup) { runSeries } // drop results for warmup
    runSeries // only take one final result
  }

  val namePad = 20
  val numPad = 15
  def runComparison(comparison: Comparison, sizes: Seq[Int], iterations: Long, duration: Duration, warmup: Int = defaultWarmup):(String,Seq[(String,Seq[(Int,Duration)])]) = {
    val durationForSingleRun = (duration / comparison.benchmarks.size / (warmup + 1)) / sizes.size
    println("Duration for single run: " + durationForSingleRun.toMillis + "ms")
    println(s"${comparison.name.replace(" ", "_").padTo(namePad, " ").mkString}${sizes.map(s => s"%${numPad}d" format s).mkString}")
    val benchmarkDuration = duration / comparison.benchmarks.size
    comparison.name -> comparison.benchmarks.map{ benchmark =>
      val seriesResult = benchmarkSeries(benchmark, sizes, iterations, benchmarkDuration, warmup)
      println(benchmark.name.replace(" ", "_").padTo(namePad, " ").mkString + seriesResult.map{ case (_, duration) => s"%${numPad}d" format duration.toNanos}.mkString)
      benchmark.name -> seriesResult
    }
  }
}
