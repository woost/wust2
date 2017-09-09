package wust.db

import wust.ids._

object Data {
  val DEFAULT = 0L

  case class User(id: UserId, name: String, isImplicit: Boolean, revision: Int)
  case class Post(id: PostId, title: String)
  case class Connection(sourceId: PostId, targetId: PostId)
  case class Containment(parentId: PostId, childId: PostId)

  case class Password(id: UserId, digest: Array[Byte])
  case class Membership(userId: UserId, groupId: GroupId)
  case class GroupInvite(groupId: GroupId, token: String)
  case class UserGroup(id: GroupId)
  case class Ownership(postId: PostId, groupId: GroupId)

  type Graph = (Iterable[Post], Iterable[Connection], Iterable[Containment], Iterable[UserGroup], Iterable[Ownership], Iterable[User], Iterable[Membership])
}
