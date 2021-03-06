package wust.webApp.views

import flatland._
import wust.graph.{Graph, TaskOrdering}
import wust.ids._
import wust.util.algorithm.dfs
import wust.util.collection._
import wust.util.macros.InlineList
import wust.webApp.state.TraverseState

import scala.collection.mutable

object KanbanData {
  def inboxNodes(graph: Graph, traverseState: TraverseState): Seq[NodeId] = graph.idToIdxFold(traverseState.parentId)(Seq.empty[NodeId]) { parentIdx =>
    val allStages: ArraySet = {
      val stages = ArraySet.create(graph.size)
      dfs.foreachStopLocally(starts = _(parentIdx), dfs.withoutStart, graph.childrenIdx, { idx =>
        val isStage = graph.nodes(idx).role == NodeRole.Stage
        if(isStage) stages += idx
        isStage
      })
      stages
    }

    val inboxTasks: Array[Int] = {
      val inboxTasks = Array.newBuilder[Int]
      graph.childrenIdx.foreachElement(parentIdx) { childIdx =>
        val node = graph.nodes(childIdx)
        if(node.role == NodeRole.Task && !traverseState.contains(node.id)) {
          @inline def hasStageParentInWorkspace = graph.parentsIdx(childIdx).exists(allStages.contains)

          if(!hasStageParentInWorkspace) inboxTasks += childIdx
        }
      }
      inboxTasks.result()
    }

    TaskOrdering.constructOrderingOf[NodeId](graph, traverseState.parentId, inboxTasks.viewMap(graph.nodeIds), identity)
  }

  //TODO: duplicate of above...
  def inboxNodesCount(graph: Graph, traverseState: TraverseState): Int = graph.idToIdxFold(traverseState.parentId)(0) { parentIdx =>
    val allStages: ArraySet = {
      val stages = ArraySet.create(graph.size)
      dfs.foreachStopLocally(starts = _(parentIdx), dfs.withoutStart, graph.childrenIdx, { idx =>
        val isStage = graph.nodes(idx).role == NodeRole.Stage
        if(isStage) stages += idx
        isStage
      })
      stages
    }

    val inboxTasks: Int = {
      var inboxTasks = 0
      graph.childrenIdx.foreachElement(parentIdx) { childIdx =>
        val node = graph.nodes(childIdx)
        if(node.role == NodeRole.Task && !traverseState.contains(node.id)) {
          @inline def hasStageParentInWorkspace = graph.parentsIdx(childIdx).exists(allStages.contains)

          if(!hasStageParentInWorkspace) inboxTasks += 1
        }
      }
      inboxTasks
    }

    inboxTasks
  }

  def columns(graph: Graph, traverseState: TraverseState): Seq[NodeId] = graph.idToIdxFold(traverseState.parentId)(Seq.empty[NodeId]){ parentIdx =>
    val columnIds = mutable.ArrayBuffer[NodeId]()
    graph.childrenIdx.foreachElement(parentIdx) { idx =>
      val node = graph.nodes(idx)
      if (node.role == NodeRole.Stage && !traverseState.contains(node.id)) {
        columnIds += node.id
      }
    }

    TaskOrdering.constructOrderingOf[NodeId](graph, traverseState.parentId, columnIds, identity)
  }

  def columnNodes(graph: Graph, traverseState: TraverseState): Seq[(NodeId, NodeRole)] = graph.idToIdxFold(traverseState.parentId)(Seq.empty[(NodeId, NodeRole)]){ parentIdx =>
    val childrenIds = mutable.ArrayBuffer[(NodeId, NodeRole)]()
    graph.childrenIdx.foreachElement(parentIdx) { childIdx =>
      val node = graph.nodes(childIdx)
      if (InlineList.contains(NodeRole.Stage, NodeRole.Task)(node.role) && !traverseState.contains(node.id)) {
        childrenIds += node.id -> node.role
      }
    }

    TaskOrdering.constructOrderingOf[(NodeId, NodeRole)](graph, traverseState.parentId, childrenIds, { case (id, _) => id })
  }
}
