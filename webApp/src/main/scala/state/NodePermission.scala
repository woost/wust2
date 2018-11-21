package wust.webApp.state

import wust.ids._
import wust.graph.{Graph, Node}
import rx._

object NodePermission {
  val feedbackNodeId = NodeId(Cuid.fromBase58("15Wooooooooostfeedback"))

  // TODO: proper permission check in frontend over member/permissions
  def canWrite(graph: Graph, nodeId: NodeId): Boolean = {
    if (nodeId == feedbackNodeId) false
    else graph.nodesById(nodeId) match {
      case n: Node.Content => n.role match {
        case NodeRole.Message | NodeRole.Task => true
        case _ => false
      }
      case _ => false
    }
  }
  def canWriteAll(graph: Graph, nodeIds: Seq[NodeId]): Boolean = nodeIds.forall(canWrite(graph, _))

  def canWrite(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): Rx[Boolean] = state.graph.map(canWrite(_, nodeId))
  def canWriteAll(state: GlobalState, nodeIds: Seq[NodeId])(implicit ctx: Ctx.Owner): Rx[Boolean] = state.graph.map(canWriteAll(_, nodeIds))
}
