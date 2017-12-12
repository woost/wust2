package wust.backend

import wust.db.Data
import wust.graph._

object DbConversions {

  implicit def forClient(post: Data.Post) = Post(post.id, post.title)
  implicit def forClient(c: Data.Connection) = Connection(c.sourceId, c.label, c.targetId)
  implicit def forClient(user: Data.User) = User(user.id, user.name, user.isImplicit, user.revision)
  implicit def forClient(group: Data.UserGroup) = Group(group.id)
  implicit def forClient(ownership: Data.Ownership) = Ownership(ownership.postId, ownership.groupId)
  implicit def forClient(membership: Data.Membership) = Membership(membership.userId, membership.groupId)

  implicit def forDb(post: Post) = Data.Post(post.id, post.title)
  implicit def forDb(user: User) = Data.User(user.id, user.name, user.isImplicit, user.revision)
  implicit def forDb(c: Connection) = Data.Connection(c.sourceId, c.label, c.targetId)
  implicit def forDb(c: Ownership) = Data.Ownership(c.postId, c.groupId)
  implicit def forDbPosts(posts: Set[Post]) = posts.map(forDb _)
  implicit def forDbUsers(users: Set[User]) = users.map(forDb _)
  implicit def forDbConnections(cs: Set[Connection]) = cs.map(forDb _)
  implicit def forDbOwnerships(cs: Set[Ownership]) = cs.map(forDb _)

  def forClient(tuple: Data.Graph): Graph = {
    val (posts, connections, groups, ownerships, users, memberships) = tuple
    Graph(
      posts.map(forClient),
      connections.map(forClient),
      groups.map(forClient),
      ownerships.map(forClient),
      users.map(forClient),
      memberships.map(forClient)
    )
  }

}
