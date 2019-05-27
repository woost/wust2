package wust.webApp.views

import flatland.{ArraySet, _}
import wust.graph.Edge.LabeledProperty
import wust.graph.{Edge, Graph, Node}
import wust.ids.{DateMilli, DateTimeMilli, DurationMilli, EdgeData, EpochMilli, NodeData, NodeId, NodeRole, ParentId, UserId}
import wust.util.algorithm.dfs
import wust.util.macros.InlineList
import wust.webApp.state.TraverseState

import scala.collection.{breakOut, mutable}

object AssignedTasksData {

  sealed trait AssignedTask {
    def nodeId: NodeId
    def parentId: NodeId
  }
  object AssignedTask {
    case class Plain(nodeId: NodeId, parentId: NodeId) extends AssignedTask
    case class Due(nodeId: NodeId, parentId: NodeId, dueDate: DateTimeMilli) extends AssignedTask
  }

  case class AssignedTasks(dueTasks: IndexedSeq[IndexedSeq[AssignedTask.Due]], tasks: IndexedSeq[AssignedTask])

  // bucket.size == result.size -1
  // result contains
  def assignedTasks(graph: Graph, userId: UserId, buckets: IndexedSeq[EpochMilli]): AssignedTasks = {
    val dueTasks = Array.fill(buckets.size)(new mutable.ArrayBuffer[AssignedTask.Due])
    val tasks = new mutable.ArrayBuffer[AssignedTask]

    val userIdx = graph.idToIdxOrThrow(userId)
    graph.assignedNodesIdx.foreachElement(userIdx) { nodeIdx =>
      val node = graph.nodes(nodeIdx)
      if (node.role == NodeRole.Task) graph.parentEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
        val edge = graph.edges(edgeIdx).as[Edge.Child]
        val parentIdx = graph.edgesIdx.a(edgeIdx)
        val parentNode = graph.nodes(parentIdx)
        @inline def isDone = graph.isDoneInAllWorkspaces(nodeIdx, Array(parentIdx))
        @inline def isWorkspace = !InlineList.contains(NodeRole.Stage, NodeRole.Task)(parentNode.role)
        if (isWorkspace && !isDone) {
          var dueDate: Option[DateTimeMilli] = None
          graph.propertiesEdgeIdx.whileElement(nodeIdx) { edgeIdx =>
            val edge = graph.edges(edgeIdx).as[Edge.LabeledProperty]
            if (edge.data.key == EdgeData.LabeledProperty.dueDate.key) {
              val propertyIdx = graph.edgesIdx.b(edgeIdx)
              graph.nodes(propertyIdx) match {
                case Node.Content(_, NodeData.DateTime(dateTime), NodeRole.Neutral, _, _) =>
                  dueDate = Some(dateTime)
                  false
                case _ => true
              }
            } else true
          }

          dueDate match {
            case Some(dueDate) =>
              val dueTask = AssignedTask.Due(node.id, parentNode.id, dueDate)
              val dueIndex = buckets.indexWhere(dueDate < _)
              if (dueIndex == -1) tasks += dueTask
              else dueTasks(dueIndex) += dueTask
            case None => tasks += AssignedTask.Plain(node.id, parentNode.id)

          }
        }
      }
    }

    AssignedTasks(dueTasks, tasks)
  }
}
