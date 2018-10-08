package wust.backend

import wust.api.{AuthUser, WebPushSubscription}
import wust.db.Data
import wust.graph._
import wust.ids._

object DbConversions {
  private def nodeMeta(node: Data.Node) = new NodeMeta(
    accessLevel = node.accessLevel
  )
  private def nodeMeta(node: Data.User) = new NodeMeta(
    accessLevel = node.accessLevel
  )

  implicit def forClient(s: Data.WebPushSubscription): WebPushSubscription =
    WebPushSubscription(s.endpointUrl, s.p256dh, s.auth)
  implicit def forClient(node: Data.Node): Node = {
    node.data match {
      case data: NodeData.Content => new Node.Content(node.id, data, nodeMeta(node))
      case data: NodeData.User    => new Node.User(UserId(node.id), data, nodeMeta(node))
    }
  }
  implicit def forClient(node: Data.User): Node.User =
    new Node.User(node.id, node.data, nodeMeta(node))
  implicit def forClientAuth(node: Data.User): AuthUser.Persisted = node.data.isImplicit match {
    case false =>
      new AuthUser.Real(node.id, node.data.name, node.data.revision)
    case true =>
      new AuthUser.Implicit(node.id, node.data.name, node.data.revision)
  }
  implicit def forDbAuth(user: AuthUser.Persisted): Data.SimpleUser = user match {
    case AuthUser.Real(id, name, revision) =>
      new Data.SimpleUser(id, new NodeData.User(name, isImplicit = false, revision))
    case AuthUser.Implicit(id, name, revision) =>
      new Data.SimpleUser(id, new NodeData.User(name, isImplicit = true, revision))
  }

  implicit def forClient(c: Data.MemberEdge): Edge = Edge.Member(c.sourceId, c.data, c.targetId)

  implicit def forClient(c: Data.Edge): Edge = Edge(c.sourceId, c.data, c.targetId)

  def forDb(u: UserId, s: WebPushSubscription): Data.WebPushSubscription =
    Data.WebPushSubscription(u, s.endpointUrl, s.p256dh, s.auth)
  implicit def forDb(node: Node): Data.Node = {
    import node._
    new Data.Node(
      id = id,
      data = data,
      accessLevel = meta.accessLevel
    )
  }

  implicit def forDb(c: Edge.Member): Data.MemberEdge =
    Data.MemberEdge(c.sourceId, c.data, c.targetId)

  implicit def forDb(c: Edge): Data.Edge =
    Data.Edge(sourceId = c.sourceId, data = c.data, targetId = c.targetId)
  implicit def forDbNodes(nodes: collection.Set[Node]): collection.Set[Data.Node] = nodes.map(forDb)
  implicit def forDbEdges(es: collection.Set[Edge]): collection.Set[Data.Edge] = es.map(forDb)

  def forClient(dbGraph: Data.Graph): Graph = {
    Graph(
      nodes = dbGraph.nodes.map(forClient),
      edges = dbGraph.edges.map(forClient)
    )
  }

}
