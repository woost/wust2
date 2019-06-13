package wust.webApp.views

import flatland.{ArraySet, _}
import wust.graph.Graph
import wust.ids.{NodeId, NodeRole, UserId}
import wust.util.algorithm.dfs
import wust.webApp.state.TraverseState

import scala.collection.{breakOut, mutable}

object ChannelTreeData {

  def invites(graph: Graph, userId: UserId): Seq[NodeId] = {
    val userIdx = graph.idToIdxOrThrow(userId)
    graph.inviteNodeIdx(userIdx).collect { case idx if !graph.pinnedNodeIdx.contains(userIdx)(idx) => graph.nodeIds(idx) }(breakOut)
  }

  def toplevelChannels(graph: Graph, userId: UserId): Seq[NodeId] = {
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

  def childrenChannels(graph: Graph, traverseState: TraverseState, userId: UserId): Seq[NodeId] = {
    val userIdx = graph.idToIdxOrThrow(userId)
    nextLayer(graph, traverseState, graph.notDeletedChildrenIdx, isChannel(_, _, userIdx))
  }

  def parentProjects(graph: Graph, traverseState: TraverseState): Seq[NodeId] = {
    val parents = nextLayer(graph, traverseState, graph.notDeletedParentsIdx, isProject)
    if (parents.isEmpty) Seq(traverseState.parentId) else parents
  }

  def childrenProjects(graph: Graph, traverseState: TraverseState): Seq[NodeId] = {
    nextLayer(graph, traverseState, graph.notDeletedChildrenIdx, isProject)
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
