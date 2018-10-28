package wust


import supertagged._

package object ids {
  type UuidType = String

  object NodeId extends TaggedType[Cuid] {
    @inline def fresh: NodeId = apply(Cuid.fromCuidString(cuid.Cuid()))
    @inline def fromBase58String(str: String): NodeId = apply(Cuid.fromBase58(str))
  }
  type NodeId = NodeId.Type

  object UserId extends OverTagged(NodeId) {
    @inline def fresh: UserId = apply(NodeId.fresh)
    @inline def fromBase58String(str: String): UserId = apply(NodeId.fromBase58String(str))
  }
  type UserId = UserId.Type

  object EpochMilli extends TaggedType[Long] {
    var delta: Long = 0 //TODO we should not have a var here, we use the delta for something very specific in the client and not for every epochmilli instance!
    @inline def localNow: EpochMilli =
      EpochMilli(System.currentTimeMillis()) // UTC: https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#currentTimeMillis--
    @inline def now: EpochMilli =
      EpochMilli(localNow + delta)
    @inline def from(time: String): EpochMilli = { // TODO this should not exist here, we cannot use instant in frontend!!
      import java.time.Instant
      EpochMilli(Instant.parse(time).toEpochMilli)
    }
    @inline def second: Long = 1000L
    @inline def minute: Long = 60L * second
    @inline def hour: Long = 60L * minute
    @inline def day: Long = 24L * hour
    @inline def week: Long = 7L * day
    implicit class RichEpochMilli(val t: EpochMilli) extends AnyVal {
      @inline def <(that: EpochMilli): Boolean = t < that
      @inline def >(that: EpochMilli): Boolean = t > that
      @inline def isBefore(that: EpochMilli): Boolean = t < that
      @inline def isAfter(that: EpochMilli): Boolean = t > that
      @inline def newest(that:EpochMilli):EpochMilli = EpochMilli((t:Long) max (that:Long))
      @inline def oldest(that:EpochMilli):EpochMilli = EpochMilli((t:Long) min (that:Long))
      def humanReadable: String = {
        // java.util.Date is deprecated, but implemented in java and scalajs
        // and therefore a simple cross-compiling solution
        import java.util.Date
        val d = new Date(t)
        val year = d.getYear + 1900
        val month = d.getMonth + 1
        val day = d.getDate
        val hour = d.getHours
        val minute = d.getMinutes
        val second = d.getSeconds
        f"$year%04d-$month%02d-$day%02d $hour%02d:$minute%02d:$second%02d"
      }
    }

    // https://www.postgresql.org/docs/9.1/static/datatype-datetime.html
    // use 1970 as minimum time (0L) due to inaccuracies in postgres when using 0001-01-01 00:00:00
    @inline def min = EpochMilli(0L)
    // use 4000-01-01 00:00:00 as maximum time instead of year 294276 (postgres maximum) for the same reason.
    @inline def max = EpochMilli(64060588800000L)
  }
  type EpochMilli = EpochMilli.Type
}
