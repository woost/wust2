package wust

import boopickle.Default._
import scalaz._
import wust.ids._

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

  implicit def PostIdPickler = transformPickler[PostId, IdType](PostId _)(Tag.unwrap _)
  implicit def GroupIdPickler = transformPickler[GroupId, IdType](GroupId _)(Tag.unwrap _)
  implicit def UserIdPickler = transformPickler[UserId, IdType](UserId _)(Tag.unwrap _)
}
