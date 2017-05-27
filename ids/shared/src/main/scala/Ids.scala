package wust

import scalaz._

package object ids {
  type IdType = Long
  // object AtomId {
  //   implicit def ordering[A <: AtomId]: Ordering[A] = Ordering.by(_.id)
  // }

  // unboxed types with scalaz: http://eed3si9n.com/learning-scalaz/Tagged+type.html
  sealed trait PostIdType
  type PostId = IdType @@ PostIdType
  implicit def PostId[T](id: T): T @@ PostIdType = Tag[T, PostIdType](id)

  sealed trait GroupIdType
  type GroupId = IdType @@ GroupIdType
  implicit def GroupId[T](id: T): T @@ GroupIdType = Tag[T, GroupIdType](id)

  sealed trait UserIdType
  type UserId = IdType @@ UserIdType
  implicit def UserId[T](id: T): T @@ UserIdType = Tag[T, UserIdType](id)
}
