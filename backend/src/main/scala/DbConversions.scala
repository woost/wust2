package wust.backend

import wust.db.data
import wust.graph._

object DbConversions {

  implicit def forClient(post: data.Post) = Post(post.id, post.title)
  implicit def forClient(c: data.Connection) = Connection(c.sourceId, c.targetId)
  implicit def forClient(c: data.Containment) = Containment(c.parentId, c.childId)
  implicit def forClient(user: data.User) = User(user.id, user.name, user.isImplicit, user.revision)
  implicit def forClient(group: data.UserGroup) = Group(group.id)
  implicit def forClient(ownership: data.Ownership) = Ownership(ownership.postId, ownership.groupId)
  implicit def forClient(membership: data.Membership) = Membership(membership.userId, membership.groupId)

  implicit def forDb(post: Post) = data.Post(post.id, post.title)
  implicit def forDb(user: User) = data.User(user.id, user.name, user.isImplicit, user.revision)
  implicit def forDb(c: Connection) = data.Connection(c.sourceId, c.targetId)
  implicit def forDb(c: Containment) = data.Containment(c.parentId, c.childId)
  implicit def forDb(c: Ownership) = data.Ownership(c.postId, c.groupId)

  def forClient(tuple: data.Graph): Graph = {
    val (posts, connections, containments, groups, ownerships, users, memberships) = tuple
    Graph(
      posts.map(forClient),
      connections.map(forClient),
      containments.map(forClient),
      groups.map(forClient),
      ownerships.map(forClient),
      users.map(forClient),
      memberships.map(forClient)
    )
  }

}
