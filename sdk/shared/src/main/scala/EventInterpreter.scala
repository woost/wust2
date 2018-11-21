package wust.sdk

import akka.io.UdpConnected.Disconnect
import wust.api.ApiEvent
import wust.graph.{Edge, Graph, GraphChanges, Node}
import wust.ids.{NodeData, NodeId}

import scala.collection.mutable

object EventInterpreter {
  import InterpretedEvent._

  def apply(graph: Graph, changes: GraphChanges): Array[InterpretedEvent] = {
    val c = changes.consistent
    val builder = Array.newBuilder[InterpretedEvent]
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

sealed trait InterpretedEvent
object InterpretedEvent {
  case class EditNode(id: NodeId, oldData: NodeData, data: NodeData) extends InterpretedEvent
  case class AddParent(id: NodeId, parentId: NodeId) extends InterpretedEvent
  case class RemoveParent(id: NodeId, parentId: NodeId) extends InterpretedEvent
}

