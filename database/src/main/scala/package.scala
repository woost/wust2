package wust

import wust.ids._

package object db {
  val DEFAULT = 0L

  case class User(id: UserId, name: String, isImplicit: Boolean, revision: Int)
  case class Post(id: PostId, title: String)
  case class Connection(id: ConnectionId, sourceId: PostId, targetId: ConnectableId)
  case class Containment(id: ContainmentId, parentId: PostId, childId: PostId)

  case class Password(id: UserId, digest: Array[Byte])
  case class Membership(groupId: GroupId, userId: UserId) //TODO: graph.Membeship has a different argument order. Alter table?
  case class GroupInvite(groupId: GroupId, token: String)
  case class UserGroup(id: GroupId)
  object UserGroup { def apply(): UserGroup = UserGroup(GroupId(0L)) }
  case class Ownership(postId: PostId, groupId: GroupId)

  type Graph = (Iterable[Post], Iterable[Connection], Iterable[Containment], Iterable[UserGroup], Iterable[Ownership], Iterable[User], Iterable[Membership])
}
