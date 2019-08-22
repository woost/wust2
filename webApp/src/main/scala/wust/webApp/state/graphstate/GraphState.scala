package wust.webApp.state.graphstate

import acyclic.file
import rx._
import flatland._
import wust.ids._
import wust.util.algorithm._
import wust.util.collection._
import wust.util.macros.InlineList
import wust.graph._
import wust.util.time.time

import scala.collection.{ breakOut, immutable, mutable }

//TODO: idempotence
//TODO: commutativity (e.g. store edges without loaded nodes until the nodes appear)
//TODO: be immune to inconsistency

final class GraphState(initialGraph: Graph) {
  import initialGraph.{ nodes, edges }
  val nodeState = new NodeState
  val edgeState = new EdgeState(nodeState)

  val children = new LayerState(edgeState, edgeDistributors.ifMyEdgeChild)
  val read = new LayerState(edgeState, edgeDistributors.ifMyEdgeRead)

  update(GraphChanges(addNodes = initialGraph.nodes, addEdges = initialGraph.edges))

  def update(changes: GraphChanges) = {
    time("graphstate") {
      val layerChanges = time("graphstate:nodestate") {nodeState.update(changes)}
      time("graphstate:edgestate") {edgeState.update(changes)}

      children.update(layerChanges)
      read.update(layerChanges)
    }
  }
}

object edgeDistributors {
  def ifMyEdgeChild(code: (NodeId, NodeId) => Unit): Edge => Unit = {
    case edge: Edge.Child => code(edge.parentId, edge.childId)
    case _                =>
  }
  def ifMyEdgeRead(code: (NodeId, NodeId) => Unit): Edge => Unit = {
    case edge: Edge.Read => code(edge.nodeId, edge.userId)
    case _                =>
  }
}
