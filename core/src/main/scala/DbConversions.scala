package wust.backend

import java.time.{Instant, LocalDateTime, ZoneId, ZoneOffset}

import wust.db.Data.{epochMilliToLocalDateTime, localDateTimeToEpochMilli}
import wust.api.WebPushSubscription
import wust.ids._
import wust.db.Data
import wust.graph._
import wust.ids.EpochMilli

object DbConversions {

  implicit def forClient(s: Data.WebPushSubscription): WebPushSubscription = WebPushSubscription(s.endpointUrl, s.p256dh, s.auth)
  implicit def forClient(post: Data.Post):Post = new Post(post.id, post.content, post.author,
    created = localDateTimeToEpochMilli(post.created),
    modified = localDateTimeToEpochMilli(post.modified),
    joinDate = JoinDate.from(localDateTimeToEpochMilli(post.joinDate)),
    joinLevel = post.joinLevel
  )
  implicit def forClient(c: Data.Connection): Connection = Connection(c.sourceId, c.label, c.targetId)
  implicit def forClient(user: Data.User): User.Persisted = {
    if (user.isImplicit) User.Implicit(user.id, user.name, user.revision)
    else User.Real(user.id, user.name, user.revision)
  }
  implicit def forClient(membership: Data.Membership): Membership = Membership(membership.userId, membership.postId)

  def forDb(u: UserId, s: WebPushSubscription): Data.WebPushSubscription = Data.WebPushSubscription(u, s.endpointUrl, s.p256dh, s.auth)
  implicit def forDb(post: Post): Data.Post = Data.Post(post.id, post.content, post.author,
    created = epochMilliToLocalDateTime(post.created),
    modified = epochMilliToLocalDateTime(post.modified),
    joinDate = epochMilliToLocalDateTime(post.joinDate.timestamp),
    joinLevel = post.joinLevel
  )
  implicit def forDb(user: User.Persisted): Data.User = user match {
    case User.Real(id, name, revision) => Data.User(id, name, isImplicit = false, revision = revision)
    case User.Implicit(id, name, revision) => Data.User(id, name, isImplicit = true, revision = revision)
  }
  implicit def forDb(c: Connection): Data.Connection = Data.Connection(c.sourceId, c.label, c.targetId)
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
