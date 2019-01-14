package wust.webApp.state

import wust.ids._
import wust.graph.{Graph, Node}
import rx._
import wust.api.AuthUser

object NodePermission {
  // TODO: proper permission check in frontend over member/permissions
  def canWrite(currentUser: AuthUser, graph: Graph, nodeId: NodeId): Boolean = {
    graph.nodesByIdGet(nodeId) match {
      case Some(n: Node.Content) => true
      case Some(n: Node.User) if !n.data.isImplicit && currentUser.id == n.id => true
      case _ => false
    }
  }
  def canWriteAll(currentUser: AuthUser, graph: Graph, nodeIds: Seq[NodeId]): Boolean = nodeIds.forall(canWrite(currentUser, graph, _))

  def canWrite(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): Rx[Boolean] = Rx { canWrite(state.user(), state.graph(), nodeId)}
  def canWriteAll(state: GlobalState, nodeIds: Seq[NodeId])(implicit ctx: Ctx.Owner): Rx[Boolean] = Rx { canWriteAll(state.user(), state.graph(), nodeIds) }
}
