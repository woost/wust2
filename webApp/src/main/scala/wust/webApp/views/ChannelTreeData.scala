package wust.webApp.views

import flatland.{ArraySet, _}
import wust.graph.{Node, Graph}
import wust.ids.{NodeId, NodeRole, UserId}
import wust.util.algorithm.dfs
import wust.webApp.state.TraverseState

import scala.collection.{breakOut, mutable}

object ChannelTreeData {

  def invites(graph: Graph, userId: UserId): Seq[NodeId] = {
    val userIdx = graph.idToIdxOrThrow(userId)
    graph.inviteNodeIdx(userIdx).collect { case idx if !graph.pinnedNodeIdx.contains(userIdx)(idx) => graph.nodeIds(idx) }(breakOut)
  }

  def toplevelChannels(graph: Graph, userId: UserId, filter: Node => Boolean): Seq[NodeId] = {
    val userIdx = graph.idToIdxOrThrow(userId)
    val pinnedNodes = ArraySet.create(graph.nodes.length)
    graph.pinnedNodeIdx.foreachElement(userIdx)(idx => if (filter(graph.nodes(idx))) pinnedNodes += idx)

    val channels = mutable.ArrayBuffer[NodeId]()
    graph.pinnedNodeIdx.foreachElement(userIdx) { idx =>
      if (filter(graph.nodes(idx))) {
        //TODO better? need to check for cycles, so you are still a toplevel channel if you are involved in a cycle
        if (!graph.ancestorsIdxExists(idx)(ancestorIdx => pinnedNodes.contains(ancestorIdx) && !graph.ancestorsIdxExists(ancestorIdx)(_ == idx))) channels += graph.nodeIds(idx)
      }
    }

    channels.sorted
  }

  def childrenChannelsOrProjects(graph: Graph, traverseState: TraverseState, userId: UserId, filter: Node => Boolean): Seq[NodeId] = {
    val userIdx = graph.idToIdxOrThrow(userId)
    nextLayer(graph, traverseState, graph.notDeletedChildrenIdx, (g,i) => (isProject(g, i) || isChannel(g, i, userIdx)) && filter(graph.nodes(i))).sortBy(idx => !isChannel(graph, graph.idToIdxOrThrow(idx), userIdx))
  }
  def childrenChannels(graph: Graph, traverseState: TraverseState, userId: UserId, filter: Node => Boolean): Seq[NodeId] = {
    val userIdx = graph.idToIdxOrThrow(userId)
    nextLayer(graph, traverseState, graph.notDeletedChildrenIdx, (g,i) => isChannel(g, i, userIdx) && filter(graph.nodes(i)))
  }

  @inline private def isProject(graph: Graph, idx: Int) = graph.nodes(idx).role == NodeRole.Project
  @inline private def isChannel(graph: Graph, idx: Int, userIdx: Int) = graph.isPinned(idx, userIdx = userIdx)

  @inline private def nextLayer(graph: Graph, traverseState: TraverseState, next: NestedArrayInt, shouldCollect: (Graph, Int) => Boolean): Seq[NodeId] = graph.idToIdxFold(traverseState.parentId)(Seq.empty[NodeId]) { parentIdx =>
    val channels = mutable.ArrayBuffer[NodeId]()
    dfs.foreachStopLocally(_(parentIdx), dfs.withoutStart, next, { idx =>
      val nodeId = graph.nodeIds(idx)
      if (traverseState.contains(nodeId)) false
      else {
        if (shouldCollect(graph, idx)) {
          channels += nodeId
          false
        } else true
      }
    })

    channels.sorted
  }
}
