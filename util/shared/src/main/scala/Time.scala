package wust.util
package time {
  final class StopWatch {
    var startTime = 0L
    var totalPassedTime = 0L

    @inline def now = System.nanoTime
    @inline def passed = now - startTime

    @inline def reset(): Unit = { totalPassedTime = 0 }
    @inline def start(): Unit = { startTime = now }
    @inline def restart(): Unit = { reset(); start() }
    @inline def stop(): Unit = { totalPassedTime += passed }

    @inline def measure[A](code: => A) = {
      start()
      val returnValue = code
      stop()
      returnValue
    }

    @inline def benchmark(n: Int)(code: => Unit): Double = {
      var i = 0
      start()
      while(i < n) {
        code
        i += 1
      }
      totalPassedTime += passed / n

      passed.toDouble / n
    }

    @inline def readNanos = if(totalPassedTime == 0) passed else totalPassedTime
    @inline def readMicros = readNanos / 1000
    @inline def readMillis = readNanos / 1000000
    @inline def readSeconds = readNanos / 1000000000.0
    @inline def readHuman: String = readHuman(3)
    @inline def readHuman(precision: Int = 8) = {
      val time = readSeconds
      val fraction = time - math.floor(time)
      var s = time.toInt
      val sb = new StringBuilder
      val d = s / 86400;
      s -= d * 86400
      if(d > 0) sb ++= "%dd " format d

      val h = s / 3600;
      s -= h * 3600
      if(h > 0) sb ++= "%dh " format h

      val m = s / 60;
      s -= m * 60
      if(m > 0) sb ++= "%dm " format m

      sb ++= "%." + precision + "fs" format (s + fraction)
      sb.toString
    }
  }
}

package object time {
  @inline def time[T](name: String)(code: => T): T = {
    val start = System.nanoTime
    val result: T = code
    val duration = (System.nanoTime - start) / 1000000.0
    println(s"$name: ${ duration }ms")
    result
  }

  object StopWatch {
    @inline def started = {
      val s = new StopWatch
      s.start()
      s
    }
  }
}
