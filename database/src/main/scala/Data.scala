package wust.db

import java.time.LocalDateTime

import wust.ids._

object Data {
  val DEFAULT = 0L

  case class User(id: UserId, name: String, isImplicit: Boolean, revision: Int)
  case class Post(id: PostId, content: String, author: UserId, created: LocalDateTime, modified: LocalDateTime)
  case class Connection(sourceId: PostId, label: Label, targetId: PostId)

  case class Password(id: UserId, digest: Array[Byte])
  case class Membership(userId: UserId, postId: PostId)

  case class WebPushSubscription(id: Long, userId: UserId, endpointUrl: String, p256dh: String, auth: String)
  object WebPushSubscription {
    def apply(userId: UserId, endpointUrl: String, p256dh: String, auth: String): WebPushSubscription = WebPushSubscription(DEFAULT, userId, endpointUrl, p256dh, auth)
  }

  object Post {
    def apply(
      id:      PostId,
      content: String,
      author:  UserId
    ) = {
      val currTime = LocalDateTime.now()
      new Post(
        id,
        content,
        author,
        currTime,
        currTime
      )
    }
  }

  type Graph = (Iterable[Post], Iterable[Connection], Iterable[User], Iterable[Membership])
}
