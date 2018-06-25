package wust

import java.time.Instant

import cuid.Cuid
import supertagged._

package object ids {
  type UuidType = String

  object NodeId extends TaggedType[UuidType] {
    def fresh: NodeId = apply(Cuid())
  }
  type NodeId = NodeId.Type

  object UserId extends OverTagged(NodeId) {
    def fresh: UserId = apply(NodeId.fresh)
  }
  type UserId = UserId.Type

  object EpochMilli extends TaggedType[Long] {
    def now:EpochMilli = EpochMilli(System.currentTimeMillis()) // UTC: https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#currentTimeMillis--
    def from(time:String) = EpochMilli(Instant.parse(time).toEpochMilli)
    implicit class RichEpochMilli(val t:EpochMilli) extends AnyVal {
      @inline def <(that:EpochMilli) = t < that
      @inline def >(that:EpochMilli) = t > that
      @inline def isBefore(that:EpochMilli) = t < that
      @inline def isAfter(that:EpochMilli) = t > that
    }

    // https://www.postgresql.org/docs/9.1/static/datatype-datetime.html
    // use 1970 as minimum time (0L) due to inaccuracies in postgres when using 0001-01-01 00:00:00
    val min = EpochMilli(0L)
    // use 4000-01-01 00:00:00 as maximum time instead of year 294276 (postgres maximum) for the same reason.
    val max = EpochMilli(64060588800000L)
  }
  type EpochMilli = EpochMilli.Type
}
