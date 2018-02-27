package wust

import java.time.Instant

import scalaz._

package object ids {
  import types._

  type IdType = Long
  type UuidType = String

  //TODO: simpler tagged types: https://github.com/acjay/taggy
  // unboxed types with scalaz: http://eed3si9n.com/learning-scalaz/Tagged+type.html
  object PostId extends UuidTypeFactory[PostIdType]
  type PostId = PostId.Type

  object GroupId extends IdTypeFactory[GroupIdType]
  type GroupId = GroupId.Type

  object UserId extends UuidTypeFactory[UserIdType]
  type UserId = UserId.Type

  object Label extends TypeFactory[String, LabelType] {
    val parent = Label("parent")
  }
  type Label = Label.Type

  object EpochMilli extends TypeFactory[Long, EpochMilliType] {
    def now:EpochMilli = EpochMilli(System.currentTimeMillis())
    def from(time:String) = EpochMilli(Instant.parse(time).toEpochMilli)
    implicit class RichEpochMilli(val t:EpochMilli) extends AnyVal {
      @inline def <(that:EpochMilli) = Tag.unwrap(t) < Tag.unwrap(that)
      @inline def >(that:EpochMilli) = Tag.unwrap(t) > Tag.unwrap(that)
      @inline def isBefore(that:EpochMilli) = t < that
      @inline def isAfter(that:EpochMilli) = t > that
    }
  }
  type EpochMilli = EpochMilli.Type
}
