package views

import flatland.ArraySet
import wust.graph.{Graph, TaskOrdering}
import wust.ids.{NodeId, UserId}
import wust.util.algorithm.dfs
import wust.webApp.state.TraverseState

import scala.collection.mutable

object ChannelTreeData {

  def toplevel(graph: Graph, userId: UserId): Seq[NodeId] = {
    val userIdx = graph.idToIdxOrThrow(userId)
    val pinnedNodes = ArraySet.create(graph.nodes.length)
    graph.pinnedNodeIdx.foreachElement(userIdx)(pinnedNodes += _)

    val channels = mutable.ArrayBuffer[NodeId]()
    graph.pinnedNodeIdx.foreachElement(userIdx) { idx =>
      //TODO better? need to check for cycles, so you are still a toplevel channel if you are involved in a cycle
      if (!graph.ancestorsIdxExists(idx)(ancestorIdx => pinnedNodes.contains(ancestorIdx) && !graph.ancestorsIdxExists(ancestorIdx)(_ == idx))) channels += graph.nodeIds(idx)
    }

    channels.sorted
  }

  def children(graph: Graph, traverseState: TraverseState, userId: UserId): Seq[NodeId] = graph.idToIdxFold(traverseState.parentId)(Seq.empty[NodeId]) { parentIdx =>
    val userIdx = graph.idToIdxOrThrow(userId)
    val channels = mutable.ArrayBuffer[NodeId]()
    dfs.withContinue(_(parentIdx), dfs.withoutStart, graph.childrenIdx, { idx =>
      val nodeId = graph.nodeIds(idx)
      if (traverseState.contains(nodeId)) false
      else {
        if (graph.isPinned(idx, userIdx = userIdx)) {
          channels += nodeId
          false
        } else true
      }
    })

    channels.sorted
  }

}
