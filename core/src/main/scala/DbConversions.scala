package wust.backend

import java.time.{Instant, LocalDateTime, ZoneId, ZoneOffset}

import io.treev.tag._
import wust.db.Data
import wust.graph._
import wust.ids.EpochMilli

object DbConversions {

  //TODO: faster time conversion?
  val utc = ZoneId.ofOffset("UTC", ZoneOffset.UTC)
  def epochMilliToLocalDateTime(t:EpochMilli):LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(t),utc)
  def localDateTimeToEpochMilli(t:LocalDateTime):EpochMilli = EpochMilli(t.toInstant(ZoneOffset.UTC).toEpochMilli)


  implicit def forClient(post: Data.Post):Post = Post(post.id, post.content, post.author, localDateTimeToEpochMilli(post.created), localDateTimeToEpochMilli(post.modified))
  implicit def forClient(c: Data.Connection): Connection = Connection(c.sourceId, c.label, c.targetId)
  implicit def forClient(user: Data.User): User.Persisted = {
    if (user.isImplicit) User.Implicit(user.id, user.name, user.revision)
    else User.Real(user.id, user.name, user.revision)
  }
  implicit def forClient(membership: Data.Membership): Membership = Membership(membership.userId, membership.postId)

  implicit def forDb(post: Post): Data.Post = Data.Post(post.id, post.content, post.author,
    epochMilliToLocalDateTime(post.created),
    epochMilliToLocalDateTime(post.modified))
  implicit def forDb(user: User.Persisted): Data.User = user match {
    case User.Real(id, name, revision) => Data.User(id, name, isImplicit = false, revision = revision)
    case User.Implicit(id, name, revision) => Data.User(id, name, isImplicit = true, revision = revision)
  }
  implicit def forDb(c: Connection): Data.Connection = Data.Connection(c.sourceId, c.label, c.targetId)
  implicit def forDbPosts(posts: Set[Post]): Set[Data.Post] = posts.map(forDb _)
  implicit def forDbUsers(users: Set[User.Persisted]): Set[Data.User] = users.map(forDb _)
  implicit def forDbConnections(cs: Set[Connection]): Set[Data.Connection] = cs.map(forDb _)

  def forClient(tuple: Data.Graph): Graph = {
    val (posts, connections, users, memberships) = tuple
    Graph(
      posts.map(forClient),
      connections.map(forClient),
      users.map(forClient),
      memberships.map(forClient)
    )
  }

}
