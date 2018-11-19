package wust.webApp.state

import wust.ids._
import wust.graph.Node
import rx._

object NodePermission {
  val feedbackNodeId = NodeId(Cuid.fromBase58("15Wooooooooostfeedback"))

  // TODO: proper permission check in frontend over member/permissions
  def canWrite(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): Rx[Boolean] = {
    if (nodeId == feedbackNodeId) Var(false)
    else state.graph.map(g => g.nodesById(nodeId) match {
      case n: Node.Content => true
      case _ => false
    })
  }
}
