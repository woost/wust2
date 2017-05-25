package wust

package object ids {
  type IdType = Long
  //TODO anyval
  // object AtomId {
  //   implicit def ordering[A <: AtomId]: Ordering[A] = Ordering.by(_.id)
  // }
  final case class PostId(id: IdType)
  object PostId { implicit def fromIdType(id: IdType): PostId = PostId(id) }

  final case class GroupId(val id: IdType)
  object GroupId { implicit def fromIdType(id: IdType): GroupId = GroupId(id) }

  final case class UserId(val id: IdType)
  object UserId { implicit def fromIdType(id: IdType): UserId = UserId(id) }
}
