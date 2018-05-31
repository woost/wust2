package wust.backend

import wust.api.{AuthUser, WebPushSubscription}
import wust.db.Data
import wust.graph._
import wust.ids._

object DbConversions {
  private def postMeta(post:Data.Node) = new NodeMeta(
      deleted = post.deleted,
      joinDate = post.joinDate,
      joinLevel = post.joinLevel
    )
  private def postMeta(post:Data.User) = new NodeMeta(
    deleted = post.deleted,
    joinDate = post.joinDate,
    joinLevel = post.joinLevel
  )

  implicit def forClient(s: Data.WebPushSubscription): WebPushSubscription = WebPushSubscription(s.endpointUrl, s.p256dh, s.auth)
  implicit def forClient(post: Data.Node):Node = {
    post.data match {
      case data:NodeData.Content => new Node.Content(post.id, data, postMeta(post))
      case data:NodeData.User => new Node.User(UserId(post.id), data, postMeta(post))
    }
  }
  implicit def forClient(post: Data.User):Node.User = new Node.User(post.id, post.data, postMeta(post))
  implicit def forClientAuth(post: Data.User):AuthUser.Persisted = post.data.isImplicit match {
    case false => new AuthUser.Real(post.id, post.data.name, post.data.revision, post.data.channelNodeId)
    case true => new AuthUser.Implicit(post.id, post.data.name, post.data.revision, post.data.channelNodeId)
  }
  implicit def forDbAuth(user: AuthUser.Persisted):Data.SimpleUser = user match {
    case AuthUser.Real(id, name, revision, channelNodeId) => new Data.SimpleUser(id, new NodeData.User(name, isImplicit = false, revision, channelNodeId))
    case AuthUser.Implicit(id, name, revision, channelNodeId) => new Data.SimpleUser(id, new NodeData.User(name, isImplicit = true, revision, channelNodeId))
  }

  implicit def forClient(c: Data.MemberEdge): Edge = Edge.Member(c.sourceId, c.data, c.targetId)

  implicit def forClient(c: Data.Edge): Edge = c.data match {
    case data:EdgeData.Author => new Edge.Author(UserId(c.sourceId), data, c.targetId)
    case data:EdgeData.Member => new Edge.Member(UserId(c.sourceId), data, c.targetId)
    case EdgeData.Parent => new Edge.Parent(c.sourceId, c.targetId)
    case data:EdgeData.Label =>  new Edge.Label(c.sourceId, data, c.targetId)
  }

  def forDb(u: UserId, s: WebPushSubscription): Data.WebPushSubscription = Data.WebPushSubscription(u, s.endpointUrl, s.p256dh, s.auth)
  implicit def forDb(post: Node): Data.Node = {
    import post._
    new Data.Node(
      id = id,
      data = data,
      deleted = meta.deleted,
      joinDate = meta.joinDate,
      joinLevel = meta.joinLevel
    )
  }

  implicit def forDb(c: Edge.Member): Data.MemberEdge = Data.MemberEdge(c.sourceId, c.data, c.targetId)

  implicit def forDb(c: Edge): Data.Edge = Data.Edge(
    sourceId = c.sourceId,
    data = c.data,
    targetId = c.targetId)
  implicit def forDbPosts(posts: Set[Node]): Set[Data.Node] = posts.map(forDb)
  implicit def forDbConnections(cs: Set[Edge]): Set[Data.Edge] = cs.map(forDb)

  def forClient(dbGraph: Data.Graph):Graph = {
    Graph(
      posts = dbGraph.posts.map(forClient),
      connections = dbGraph.connections.map(forClient)
    )
  }

}
