package wust

package object ids {
  //TODO: this also needs to be done as database contstraint
  type IdType = Long
  //TODO anyval
  sealed trait AtomId {
    def id: IdType
  }
  object AtomId {
    implicit def ordering[A <: AtomId]: Ordering[A] = Ordering.by(_.id)
  }
  sealed trait ConnectableId extends AtomId
  final case class PostId(id: IdType) extends ConnectableId
  object PostId { implicit def fromIdType(id: IdType): PostId = PostId(id) }
  final case class ConnectsId(id: IdType) extends ConnectableId
  object ConnectsId { implicit def fromIdType(id: IdType): ConnectsId = ConnectsId(id) }
  final case class ContainsId(id: IdType) extends AtomId
  object ContainsId { implicit def fromIdType(id: IdType): ContainsId = ContainsId(id) }
  final case class UnknownConnectableId(id: IdType) extends ConnectableId

  final case class GroupId(val id: IdType)
  final case class UserId(val id: IdType)
}
