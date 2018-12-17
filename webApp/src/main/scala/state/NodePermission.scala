package wust.webApp.state

import wust.ids._
import wust.graph.{Graph, Node}
import rx._

object NodePermission {
  // TODO: proper permission check in frontend over member/permissions
  def canWrite(graph: Graph, nodeId: NodeId): Boolean = {
    graph.nodesByIdGet(nodeId) match {
      case Some(n: Node.Content) => true
      case _ => false
    }
  }
  def canWriteAll(graph: Graph, nodeIds: Seq[NodeId]): Boolean = nodeIds.forall(canWrite(graph, _))

  def canWrite(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): Rx[Boolean] = state.graph.map(canWrite(_, nodeId))
  def canWriteAll(state: GlobalState, nodeIds: Seq[NodeId])(implicit ctx: Ctx.Owner): Rx[Boolean] = state.graph.map(canWriteAll(_, nodeIds))
}
