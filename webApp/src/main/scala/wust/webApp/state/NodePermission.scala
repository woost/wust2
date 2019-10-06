package wust.webApp.state

import rx._
import wust.api.AuthUser
import wust.graph.{Graph, Node}
import wust.ids._

object NodePermission {
  // TODO: proper permission check in frontend over member/permissions
  def canWrite(currentUserId: UserId, graph: Graph, nodeId: NodeId): Boolean = {
    graph.nodesById(nodeId) match {
      case Some(n: Node.Content) => true
      case Some(n: Node.User) if !n.data.isImplicit && currentUserId == n.id => true
      case _ => false
    }
  }
  def canWriteAll(currentUserId: UserId, graph: Graph, nodeIds: Seq[NodeId]): Boolean = nodeIds.forall(canWrite(currentUserId, graph, _))

  def canWrite(nodeId: NodeId)(implicit ctx: Ctx.Owner): Rx[Boolean] = Rx { canWrite(GlobalState.userId(), GlobalState.graph(), nodeId)}
  def canWriteAll(nodeIds: Seq[NodeId])(implicit ctx: Ctx.Owner): Rx[Boolean] = Rx { canWriteAll(GlobalState.userId(), GlobalState.graph(), nodeIds) }
}
