package wust.backend

import wust.ids._
import wust.db
import wust.graph._

package object dbConversions {
  implicit def forClient(post: db.Post) = Post(post.id, post.title)
  implicit def forDb(post: Post) = db.Post(post.id, post.title)
  implicit def forClient(c: db.Connection) = Connection(c.id, c.sourceId, c.targetId)
  implicit def forClient(c: db.Containment) = Containment(c.id, c.parentId, c.childId)
  implicit def forClient(user: db.User) = User(user.id, user.name, user.isImplicit, user.revision)
  implicit def forDb(user: User) = db.User(user.id, user.name, user.isImplicit, user.revision)
  implicit def forClient(group: db.UserGroup) = Group(group.id)
  implicit def forClient(ownership: db.Ownership) = Ownership(ownership.postId, ownership.groupId)

  def forClient(tuple: db.graph.Graph): Graph = {
    val (posts, connections, containments, groups, ownerships, users, memberships) = tuple
    Graph(
      posts.map(forClient),
      connections.map(forClient),
      containments.map(forClient),
      groups.map(forClient),
      ownerships.map(forClient),
      users.map(forClient),
      memberships.flatMap {
        case db.Membership(groupId, Some(userId)) => Set(Membership(userId, groupId))
        // a membership without user means a membership for all users
        case db.Membership(groupId, None) => users.map(user => Membership(user.id, groupId))
      }
    )
  }

}
