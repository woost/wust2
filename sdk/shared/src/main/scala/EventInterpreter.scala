package wust.sdk

import akka.io.UdpConnected.Disconnect
import wust.api.ApiEvent
import wust.graph.{Edge, Graph, GraphChanges, Node}
import wust.ids.{NodeData, NodeId}

import scala.collection.mutable

object EventInterpreter {
  import NodeData.Info._

  def apply(graph: Graph, changes: GraphChanges): Array[NodeData.Info] = {
    val c = changes.consistent
    val builder = Array.newBuilder[NodeData.Info]
    val newNodesBuilder = mutable.HashSet.newBuilder[NodeId]
    c.addNodes.foreach {
      case n: Node.Content =>
        val oldNode = graph.nodesByIdGet(n.id)
        oldNode match {
          case None => newNodesBuilder += n.id
          case Some(oldNode) => builder += EditNode(n.id, oldNode.data, n.data)
        }
      case _ =>
    }

    val newNodes = newNodesBuilder.result()

    c.addEdges.foreach {
      case e: Edge.Parent =>
        if (!newNodes(e.childId)) builder += AddParent(e.childId, e.parentId)
      case _ =>
    }
    c.delEdges.foreach {
      case e: Edge.Parent =>
        builder += RemoveParent(e.childId, e.parentId)
      case _ =>
    }

    builder.result()
  }
}
