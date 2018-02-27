package wust.ids.types

import wust.ids.{IdType, UuidType}

sealed trait PostIdType
sealed trait GroupIdType
sealed trait UserIdType
sealed trait LabelType
sealed trait EpochMilliType

private[ids] trait TypeFactory[RawType, TagType] {
  import scalaz._
  type Type = RawType @@ TagType
  def apply(id: RawType): Type = Tag[RawType, TagType](id)
}

private[ids] trait IdTypeFactory[RawType] extends TypeFactory[IdType, RawType]
private[ids] trait UuidTypeFactory[RawType] extends TypeFactory[UuidType, RawType] {
  import cuid.Cuid
  def fresh: Type = apply(Cuid())
}
