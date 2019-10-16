package wust.webApp.views

import flatland.{ArraySet, _}
import wust.graph.{Graph, Node}
import wust.ids.{NodeId, NodeRole, UserId}
import wust.util.algorithm.dfs
import wust.util.collection._
import wust.webApp.state.TraverseState

import scala.collection.{breakOut, mutable}

object ChannelTreeData {

  def invites(graph: Graph, userId: UserId): Seq[NodeId] = {
    val userIdx = graph.idToIdxOrThrow(userId)
    graph.inviteNodeIdx(userIdx).collect { case idx if !graph.pinnedNodeIdx.contains(userIdx)(idx) => graph.nodeIds(idx) }(breakOut)
  }

  def toplevelChannels(graph: Graph, userId: UserId, filter: Node => Boolean): Seq[NodeId] = {
    val userIdx = graph.idToIdxOrThrow(userId)
    toplevelLayer(graph, userIdx, filter, _ => Seq.empty[NodeId])
  }

  def toplevelChannelsOrProjects(graph: Graph, userId: UserId, filter: Node => Boolean): Seq[NodeId] = {
    val userIdx = graph.idToIdxOrThrow(userId)
      //TODO performance not ideal for double sorting with calling childrenChannelsOrProjects internally
    toplevelLayer(graph, userIdx, filter, idx => childrenChannelsOrProjects(graph, TraverseState(graph.nodeIds(idx)), userId, filter))
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

  @inline private def toplevelLayer(graph: Graph, userIdx: Int, filter: Node => Boolean, alternative: Int => Seq[NodeId]): Seq[NodeId] = {
    val pinnedNodes = ArraySet.create(graph.nodes.length)
    graph.pinnedNodeIdx.foreachElement(userIdx)(idx => if (filter(graph.nodes(idx))) pinnedNodes += idx)

    val channels = distinctBuilder[NodeId, mutable.ArrayBuffer]
    graph.pinnedNodeIdx.foreachElement(userIdx) { idx =>
      if (pinnedNodes.contains(idx)) {
        if (!graph.ancestorsIdxExists(idx)(ancestorIdx => pinnedNodes.contains(ancestorIdx) && !graph.ancestorsIdxExists(ancestorIdx)(_ == idx))) channels += graph.nodeIds(idx)
      } else {
        channels ++= alternative(idx)
      }
    }

    channels.result.sorted
  }

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
