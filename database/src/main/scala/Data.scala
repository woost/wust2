package wust.db

import java.time.{Instant, LocalDateTime, ZoneOffset}

import wust.ids._

import scala.collection.mutable

object Data {
  def epochMilliToLocalDateTime(t:EpochMilli):LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(t),ZoneOffset.UTC)
  def localDateTimeToEpochMilli(t:LocalDateTime):EpochMilli = EpochMilli(t.toInstant(ZoneOffset.UTC).toEpochMilli)

  val DEFAULT = 0L

  case class User(id: UserId, name: String, isImplicit: Boolean, revision: Int)
  case class Post(id: PostId, content: String, author: UserId, created: LocalDateTime, modified: LocalDateTime, joinDate: LocalDateTime, joinLevel: AccessLevel)
  case class Connection(sourceId: PostId, label: Label, targetId: PostId)

  case class Password(id: UserId, digest: Array[Byte])
  case class Membership(userId: UserId, postId: PostId, level:AccessLevel)

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
        id = id,
        content = content,
        author = author,
        created = currTime,
        modified = currTime,
        joinDate = epochMilliToLocalDateTime(JoinDate.Never.timestamp),
        joinLevel = AccessLevel.ReadWrite
      )
    }
  }

  // adjacency list which comes out of postgres stored procedure graph_page(parents, children)
  case class GraphRow(postId: PostId, content: String, author: UserId, created: LocalDateTime, modified: LocalDateTime, joinDate: LocalDateTime, joinLevel:AccessLevel, targetIds: List[PostId], labels: List[Label])
  case class Graph(posts: Seq[Post], connections:Seq[Connection])
  object Graph {
    def from(rowsList:List[GraphRow]):Graph = {
      val rows = rowsList.toArray
      val posts = mutable.ArrayBuffer.empty[Post]
      val connections = mutable.ArrayBuffer.empty[Connection]
      var i = 0
      var j = 0
      while( i < rows.length ) {
        val row = rows(i)
        val labels = row.labels
        val targetIds = row.targetIds
        val post = Post(row.postId, row.content, row.author, row.created, row.modified, row.joinDate, row.joinLevel)

        posts += post

        j = 0
        while(j < row.labels.length) {
          val label = labels(j)
          val targetId = targetIds(j)
          connections += Connection(sourceId = post.id, label, targetId)
          j += 1
        }

        i += 1
      }
      Graph(posts, connections)
    }
  }
}
