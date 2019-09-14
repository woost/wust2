package wust.webApp.views

import wust.ids._
import wust.graph._

object NodeTypeSelector {
  //TODO: need to check whether the node is at the right position ing raph: directchildren, deepchildren. at least in traversal that is already done? But assume deep children chain.
  def isSelected(selection: NodeTypeSelection)(node: Node): Boolean = selection match {
    case NodeTypeSelection.Data(dataTpe) => node.data.tpe == dataTpe
    case NodeTypeSelection.Ref => true
    case NodeTypeSelection.DeepChildrenChain(role) => node.role == role
    case NodeTypeSelection.DirectChildren(role) => node.role == role
  }

  def collect(selection: NodeTypeSelection)(graph: Graph, startId: NodeId): Seq[NodeId] = selection match {
    case NodeTypeSelection.Data(dataTpe) => ???
    case NodeTypeSelection.DeepChildrenChain(role) => ???
    case NodeTypeSelection.DirectChildren(role) => ???
    case NodeTypeSelection.Ref => ???
  }
}
