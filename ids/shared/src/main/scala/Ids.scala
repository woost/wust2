package wust

import scalaz._

package object ids {
  type IdType = Long

  // unboxed types with scalaz: http://eed3si9n.com/learning-scalaz/Tagged+type.html
  sealed trait PostIdType
  type PostId = IdType @@ PostIdType
  implicit def PostId(id: IdType): PostId = Tag[IdType, PostIdType](id)

  sealed trait GroupIdType
  type GroupId = IdType @@ GroupIdType
  implicit def GroupId(id: IdType): GroupId = Tag[IdType, GroupIdType](id)

  sealed trait UserIdType
  type UserId = IdType @@ UserIdType
  implicit def UserId(id: IdType): UserId = Tag[IdType, UserIdType](id)
}
