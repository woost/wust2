package wust.db

import java.time.LocalDateTime

import wust.ids._

object Data {
  val DEFAULT = 0L

  case class User(id: UserId, name: String, isImplicit: Boolean, revision: Int)
  case class Post(id: PostId, content: String, author: UserId, created: LocalDateTime, modified: LocalDateTime)
  case class Connection(sourceId: PostId, label: Label, targetId: PostId)

  case class Password(id: UserId, digest: Array[Byte])
  case class Membership(userId: UserId, groupId: GroupId)
  case class GroupInvite(groupId: GroupId, token: String)
  case class UserGroup(id: GroupId)
  case class Ownership(postId: PostId, groupId: GroupId)

  object Post {
    def apply(
               id: PostId,
               content: String,
               author: UserId) = {
        val currTime = LocalDateTime.now();
        new Post(
          id,
          content,
          author,
          currTime,
          currTime
        )
    }
  }

  type Graph = (Iterable[Post], Iterable[Connection], Iterable[UserGroup], Iterable[Ownership], Iterable[User], Iterable[Membership])
}
