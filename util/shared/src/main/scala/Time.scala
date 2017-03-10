package wust.util
package object time {
  def time[T](name: String)(code: => T): T = {
    val start = System.nanoTime
    val result: T = code
    val duration = (System.nanoTime - start) / 1000
    println(s"$name: ${duration}us")
    result
  }

  object StopWatch {
    def started = {
      val s = new StopWatch
      s.start()
      s
    }
  }

  class StopWatch {
    var startTime = 0L
    var totalPassedTime = 0L

    def now = System.nanoTime
    def passed = now - startTime

    def reset() { totalPassedTime = 0 }
    def start() { startTime = now }
    def restart() { reset(); start() }
    def stop() { totalPassedTime += passed }

    def measure[A](code: => A) = {
      start()
      val returnValue = code
      stop()
      returnValue
    }

    def benchmark(n: Int)(code: => Unit): Double = {
      var i = 0
      start()
      while (i < n) {
        code
        i += 1
      }
      totalPassedTime += passed / n

      passed.toDouble / n
    }

    def readNanos = if (totalPassedTime == 0) passed else totalPassedTime
    def readMicros = readNanos / 1000
    def readMillis = readNanos / 1000000
    def readSeconds = readNanos / 1000000000.0
    def readHuman: String = readHuman(3)
    def readHuman(precision: Int = 8) = {
      val time = readSeconds
      val fraction = time - math.floor(time)
      var s = time.toInt
      val sb = new StringBuilder
      val d = s / 86400; s -= d * 86400
      if (d > 0) sb ++= "%dd " format d

      val h = s / 3600; s -= h * 3600
      if (h > 0) sb ++= "%dh " format h

      val m = s / 60; s -= m * 60
      if (m > 0) sb ++= "%dm " format m

      sb ++= "%." + precision + "fs" format (s + fraction)
      sb.toString
    }
  }
}
