package wust



import supertagged._

package object ids {
  type UuidType = String

  object NodeId extends TaggedType[Cuid] {
    def fresh: NodeId = apply(Cuid.fromCuidString(cuid.Cuid()))
  }
  type NodeId = NodeId.Type

  object UserId extends OverTagged(NodeId) {
    def fresh: UserId = apply(NodeId.fresh)
  }
  type UserId = UserId.Type

  object EpochMilli extends TaggedType[Long] {
    def now: EpochMilli =
      EpochMilli(System.currentTimeMillis()) // UTC: https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#currentTimeMillis--
    def from(time: String) = {
      import java.time.Instant
      EpochMilli(Instant.parse(time).toEpochMilli)
    }
    implicit class RichEpochMilli(val t: EpochMilli) extends AnyVal {
      @inline def <(that: EpochMilli) = t < that
      @inline def >(that: EpochMilli) = t > that
      @inline def isBefore(that: EpochMilli) = t < that
      @inline def isAfter(that: EpochMilli) = t > that
      def humanReadable:String = {
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
    val min = EpochMilli(0L)
    // use 4000-01-01 00:00:00 as maximum time instead of year 294276 (postgres maximum) for the same reason.
    val max = EpochMilli(64060588800000L)
  }
  type EpochMilli = EpochMilli.Type
}
