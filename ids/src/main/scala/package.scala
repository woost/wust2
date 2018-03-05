package wust

import java.time.Instant
import cuid.Cuid

import io.treev.tag._

package object ids {
  type IdType = Long
  type UuidType = String

  object PostId extends TaggedType[UuidType] {
    def fresh: PostId = apply(Cuid())
  }
  type PostId = PostId.Type

  object UserId extends TaggedType[UuidType] {
    def fresh: UserId = apply(Cuid())
  }
  type UserId = UserId.Type

  object Label extends TaggedType[String] {
    val parent = Label("parent")
  }
  type Label = Label.Type

  object EpochMilli extends TaggedType[Long] {
    def now:EpochMilli = EpochMilli(System.currentTimeMillis())
    def from(time:String) = EpochMilli(Instant.parse(time).toEpochMilli)
    implicit class RichEpochMilli(val t:EpochMilli) extends AnyVal {
      @inline def <(that:EpochMilli) = t < that
      @inline def >(that:EpochMilli) = t > that
      @inline def isBefore(that:EpochMilli) = t < that
      @inline def isAfter(that:EpochMilli) = t > that
    }
  }
  type EpochMilli = EpochMilli.Type
}
