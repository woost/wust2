package wust.backend

import java.time.{Instant, LocalDateTime, ZoneId, ZoneOffset}

import wust.db.Data.{epochMilliToLocalDateTime, localDateTimeToEpochMilli}
import wust.api.WebPushSubscription
import wust.ids._
import wust.db.Data
import wust.graph._
import wust.ids.EpochMilli

import wust.ids.serialize.Circe._
import io.circe._, io.circe.syntax._, io.circe.generic.extras.semiauto._, io.circe.parser._

object DbConversions {

  implicit def forClient(s: Data.WebPushSubscription): WebPushSubscription = WebPushSubscription(s.endpointUrl, s.p256dh, s.auth)
  implicit def forClient(post: Data.Post):Post = new Post(
    id = post.id,
    content = post.content, //TODO: what about failure? move to db.scala like connection?
    deleted = localDateTimeToEpochMilli(post.deleted),
    author = post.author,
    created = localDateTimeToEpochMilli(post.created),
    modified = localDateTimeToEpochMilli(post.modified),
    joinDate = JoinDate.from(localDateTimeToEpochMilli(post.joinDate)),
    joinLevel = post.joinLevel
  )
  implicit def forClient(c: Data.Connection): Connection = Connection(
    c.sourceId,
    c.content,
    c.targetId)
  implicit def forClient(user: Data.User): User.Persisted = {
    if (user.isImplicit) User.Implicit(user.id, user.name, user.revision, channelPostId = user.channelPostId, userPostId = user.userPostId)
    else User.Real(user.id, user.name, user.revision, channelPostId = user.channelPostId, userPostId = user.userPostId)
  }
  implicit def forClient(membership: Data.Membership): Membership = Membership(membership.userId, membership.postId)

  def forDb(u: UserId, s: WebPushSubscription): Data.WebPushSubscription = Data.WebPushSubscription(u, s.endpointUrl, s.p256dh, s.auth)
  implicit def forDb(post: Post): Data.Post = Data.Post(
    id = post.id,
    content = post.content,
    deleted = epochMilliToLocalDateTime(post.deleted),
    author = post.author,
    created = epochMilliToLocalDateTime(post.created),
    modified = epochMilliToLocalDateTime(post.modified),
    joinDate = epochMilliToLocalDateTime(post.joinDate.timestamp),
    joinLevel = post.joinLevel
  )
  implicit def forDb(user: User.Persisted): Data.User = user match {
    case User.Real(id, name, revision, channelPostId, userPostId) => Data.User(id = id, name = name, isImplicit = false, revision = revision, channelPostId = channelPostId, userPostId = userPostId)
    case User.Implicit(id, name, revision, channelPostId, userPostId) => Data.User(id = id, name = name, isImplicit = true, revision = revision, channelPostId = channelPostId, userPostId = userPostId)
  }
  implicit def forDb(c: Connection): Data.Connection = Data.Connection(
    sourceId = c.sourceId,
    content = c.content,
    targetId = c.targetId)
  implicit def forDbPosts(posts: Set[Post]): Set[Data.Post] = posts.map(forDb)
  implicit def forDbUsers(users: Set[User.Persisted]): Set[Data.User] = users.map(forDb)
  implicit def forDbConnections(cs: Set[Connection]): Set[Data.Connection] = cs.map(forDb)

  def forClient(dbGraph: Data.Graph):Graph = {
    Graph(
      posts = dbGraph.posts.map(forClient),
      connections = dbGraph.connections.map(forClient),
      users = Nil,
      memberships = Nil
    )
  }

}
