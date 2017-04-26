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
  case class PostId(id: IdType) extends ConnectableId
  object PostId { implicit def fromIdType(id: IdType): PostId = PostId(id) }
  case class ConnectsId(id: IdType) extends ConnectableId
  object ConnectsId { implicit def fromIdType(id: IdType): ConnectsId = ConnectsId(id) }
  case class ContainsId(id: IdType) extends AtomId
  object ContainsId { implicit def fromIdType(id: IdType): ContainsId = ContainsId(id) }
  case class UnknownConnectableId(id: IdType) extends ConnectableId

  //TODO: wrap GroupId, UserId like PostId -> also in db.scala
  type GroupId = Long
  type UserId = Long
}
