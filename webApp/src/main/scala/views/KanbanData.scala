package wust.webApp.views

import flatland._
import wust.graph.{Graph, Node, TaskOrdering, Tree}
import wust.ids._
import wust.util.algorithm.dfs
import wust.util.macros.InlineList

object KanbanData {
  def inboxNodes(graph: Graph, focusedId: NodeId): Seq[NodeId] = {
    val focusedIdx = graph.idToIdx(focusedId)

    val topLevelStages = graph.childrenIdx(focusedIdx).filter(idx => graph.nodes(idx).role == NodeRole.Stage)
    val allStages: ArraySet = {
      val stages = ArraySet.create(graph.size)
      topLevelStages.foreachElement(stages.add)
      dfs.withContinue(starts = topLevelStages.foreachElement, dfs.afterStart, graph.childrenIdx, { idx =>
        val isStage = graph.nodes(idx).role == NodeRole.Stage
        if(isStage) stages += idx
        isStage
      })
      stages
    }

    val inboxTasks: ArraySet = {
      val inboxTasks = ArraySet.create(graph.size)
      graph.childrenIdx.foreachElement(focusedIdx) { childIdx =>
        if(graph.nodes(childIdx).role == NodeRole.Task) {
          @inline def hasStageParentInWorkspace = graph.parentsIdx(childIdx).exists(allStages.contains)

          if(!hasStageParentInWorkspace) inboxTasks += childIdx
        }
      }
      inboxTasks
    }

    TaskOrdering.constructOrderingOf[NodeId](graph, focusedId, inboxTasks.map(graph.nodeIds(_)), identity)
  }

  def columns(graph: Graph, focusedId: NodeId): Seq[NodeId] = {
    val focusedIdx = graph.idToIdx(focusedId)

    val columnIds = graph.childrenIdx.flatMap[NodeId](focusedIdx) { idx =>
      val node = graph.nodes(idx)
      if (node.role == NodeRole.Stage) Array(node.id) else Array()
    }

    TaskOrdering.constructOrderingOf[NodeId](graph, focusedId, columnIds, identity)
  }

  def columnNodes(graph: Graph, focusedId: NodeId): Seq[(NodeId, NodeRole)] = {
    graph.idToIdxGet(focusedId).fold(Seq.empty[(NodeId, NodeRole)]){ nodeIdx =>
      val childrenIds = graph.childrenIdx.flatMap[(NodeId, NodeRole)](nodeIdx) { childIdx =>
        val node = graph.nodes(childIdx)
        if (InlineList.contains(NodeRole.Stage, NodeRole.Task)(node.role)) Array((node.id, node.role)) else Array()
      }

      TaskOrdering.constructOrderingOf[(NodeId, NodeRole)](graph, focusedId, childrenIds, { case (id, _) => id })
    }
  }
}
