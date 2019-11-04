package wust.core

import wust.api.{AuthUser, UserDetail, NodeTemplate, WebPushSubscription}
import wust.db.Data
import wust.graph.{Edge, Graph, Node, NodeMeta}
import wust.ids.{NodeData, UserId}

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
      case data: NodeData.Content => new Node.Content(node.id, data, node.role, nodeMeta(node), node.views, node.settings)
      case data: NodeData.User    => new Node.User(UserId(node.id), data, nodeMeta(node), node.views, node.settings)
    }
  }
  implicit def forClient(userDetail: Data.UserDetail): UserDetail = {
    UserDetail(userDetail.userId, userDetail.email, userDetail.verified, userDetail.plan)
  }
  implicit def forClient(template: Data.NodeTemplate): NodeTemplate = {
    NodeTemplate(template.name, template.nodeId)
  }
  implicit def forClient(node: Data.User): Node.User =
    new Node.User(node.id, node.data, nodeMeta(node))
  implicit def forClientAuth(node: Data.User): AuthUser.Persisted = node.data.isImplicit match {
    case false =>
      new AuthUser.Real(node.id, node.data.name, node.data.revision, node.data.imageFile)
    case true =>
      new AuthUser.Implicit(node.id, node.data.name, node.data.revision, node.data.imageFile)
  }
  implicit def forDbAuth(user: AuthUser.Persisted): Data.SimpleUser = user match {
    case AuthUser.Real(id, name, revision, imageFile) =>
      new Data.SimpleUser(id, new NodeData.User(name, isImplicit = false, revision, imageFile))
    case AuthUser.Implicit(id, name, revision, imageFile) =>
      new Data.SimpleUser(id, new NodeData.User(name, isImplicit = true, revision, imageFile))
  }

  implicit def forClient(c: Data.MemberEdge): Edge = Edge.Member(c.sourceId, c.data, c.targetId)

  implicit def forClient(c: Data.Edge): Edge = Edge(c.sourceId, c.data, c.targetId)

  implicit def forDb(template: NodeTemplate): Data.NodeTemplate = {
    Data.NodeTemplate(template.name, template.nodeId)
  }

  def forDb(u: UserId, s: WebPushSubscription): Data.WebPushSubscription =
    Data.WebPushSubscription(u, s.endpointUrl, s.p256dh, s.auth)
  implicit def forDb(node: Node): Data.Node = {
    import node._
    new Data.Node(
      id = id,
      data = data,
      role = role,
      accessLevel = meta.accessLevel,
      settings = node.settings,
      views = node.views
    )
  }

  implicit def forDb(c: Edge.Member): Data.MemberEdge =
    Data.MemberEdge(c.nodeId, c.data, c.userId)

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
