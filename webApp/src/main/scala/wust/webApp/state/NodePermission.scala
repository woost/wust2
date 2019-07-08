package wust.webApp.state

import rx._
import wust.api.AuthUser
import wust.graph.{Graph, Node}
import wust.ids._

object NodePermission {
  // TODO: proper permission check in frontend over member/permissions
  def canWrite(currentUser: AuthUser, graph: Graph, nodeId: NodeId): Boolean = {
    graph.nodesById(nodeId) match {
      case Some(n: Node.Content) => true
      case Some(n: Node.User) if !n.data.isImplicit && currentUser.id == n.id => true
      case _ => false
    }
  }
  def canWriteAll(currentUser: AuthUser, graph: Graph, nodeIds: Seq[NodeId]): Boolean = nodeIds.forall(canWrite(currentUser, graph, _))

  def canWrite(nodeId: NodeId)(implicit ctx: Ctx.Owner): Rx[Boolean] = Rx { canWrite(GlobalState.user(), GlobalState.graph(), nodeId)}
  def canWriteAll(nodeIds: Seq[NodeId])(implicit ctx: Ctx.Owner): Rx[Boolean] = Rx { canWriteAll(GlobalState.user(), GlobalState.graph(), nodeIds) }
}
