package wust

import scalaz._

package object ids {
  import types._

  type IdType = Long
  type UuidType = String

  //TODO: simpler tagged types: https://github.com/acjay/taggy
  // unboxed types with scalaz: http://eed3si9n.com/learning-scalaz/Tagged+type.html
  object PostId extends UuidTypeFactory[PostIdType]
  type PostId = PostId.Type
  implicit def UuidTypeIsPostId(id: UuidType) = PostId(id)

  object GroupId extends IdTypeFactory[GroupIdType]
  type GroupId = GroupId.Type
  implicit def IdTypeIsGroupId(id: IdType): GroupId = GroupId(id)

  object UserId extends UuidTypeFactory[UserIdType]
  type UserId = UserId.Type
  implicit def UuidTypeIsUserId(id: UuidType): UserId = UserId(id)

  object Label extends TypeFactory[String, LabelType] {
    val parent = Label("parent")
  }
  type Label = Label.Type
  implicit def StringIsLabel(id: String): Label = Label(id)

}
