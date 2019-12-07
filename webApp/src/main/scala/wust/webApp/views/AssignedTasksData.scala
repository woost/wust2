package wust.webApp.views

import wust.graph.{Edge, Graph, Node}

import scala.scalajs.js
import wust.ids._
import wust.util.macros.InlineList

import scala.collection.{immutable, mutable}

object AssignedTasksData {

  final case class AssignedTasksStat(user: Node.User, numTasks: Int)

  sealed trait AssignedTask {
    def nodeId: NodeId
    def parentId: NodeId
  }
  object AssignedTask {
    final case class Plain(nodeId: NodeId, parentId: NodeId) extends AssignedTask
    final case class Due(nodeId: NodeId, parentId: NodeId, dueDate: DateTimeMilli) extends AssignedTask
  }

  final case class AssignedTasks(dueTasks: IndexedSeq[IndexedSeq[AssignedTask.Due]], tasks: IndexedSeq[AssignedTask])
  def assignedTasksStats(graph: Graph, focusedId: NodeId, userId: Seq[UserId]): Seq[AssignedTasksStat] = {
    val focusedIdx = graph.idToIdxOrThrow(focusedId)
    val userIdxs = userId.map(graph.idToIdxOrThrow(_))

    userIdxs.map { userIdx =>
      val user = graph.nodes(userIdx).asInstanceOf[Node.User]
      val assignedTaskCount = graph.assignedNodesIdx(userIdx).count(nodeIdx =>
        graph.notDeletedChildrenIdx(focusedIdx).contains(nodeIdx) &&
        graph.nodes(nodeIdx).role == NodeRole.Task &&
        !graph.isDoneInAllWorkspaces(nodeIdx, Array(focusedIdx))
      )
      AssignedTasksStat(user, assignedTaskCount)
    }
  }

  // bucket.size == result.size -1
  // result contains
  def assignedTasks(graph: Graph, focusedId: NodeId, userId: Option[UserId], deepSearch: Boolean): AssignedTasks = {
    val focusedIdx = graph.idToIdxOrThrow(focusedId)
    val userIdx = userId.map(graph.idToIdxOrThrow(_))

    val now = EpochMilli.now
    val buckets = DueDate.DueBucket.values

    val dueTasks = Array.fill(buckets.size)(new mutable.ArrayBuffer[AssignedTask.Due])
    val tasks = new mutable.ArrayBuffer[AssignedTask]

    graph.descendantsIdxWithContinue(focusedIdx) { nodeIdx =>
      val node = graph.nodes(nodeIdx)
      val noUserChoosen = userId.isEmpty
      val thisUserAssigned = userIdx match {
        case Some(uidx) => graph.assignedUsersIdx.contains(nodeIdx)(uidx)
        case _ => false
      }

      // parents.exists is not really correct here, because in case of multiple parents we just include the first
      // parent we find and therefore clicking done on a task there will only check it in this one parent.
      if(node.role == NodeRole.Task) {
        if (noUserChoosen || thisUserAssigned) graph.parentEdgeIdx.exists(nodeIdx) { edgeIdx =>
          val parentIdx = graph.edgesIdx.a(edgeIdx)
          val parentNode = graph.nodes(parentIdx)

          @inline def isDone = graph.isDoneInAllWorkspaces(nodeIdx, Array(parentIdx))

          @inline def isWorkspace = !InlineList.contains(NodeRole.Stage, NodeRole.Tag)(parentNode.role)

          if(isWorkspace && !isDone) {
            var dueDate: Option[DateTimeMilli] = None
            graph.propertiesEdgeIdx.whileElement(nodeIdx) { edgeIdx =>
              val edge = graph.edges(edgeIdx).as[Edge.LabeledProperty]
              if(edge.data.key == EdgeData.LabeledProperty.dueDate.key) {
                val propertyIdx = graph.edgesIdx.b(edgeIdx)
                graph.nodes(propertyIdx) match {
                  case Node.Content(_, NodeData.DateTime(dateTime), NodeRole.Neutral, _, _, _) =>
                    dueDate = Some(dateTime)
                    false
                  case _                                                                    => true
                }
              } else true
            }

            dueDate match {
              case Some(dueDate) =>
                val dueTask = AssignedTask.Due(node.id, parentNode.id, dueDate)
                val dueIndex = buckets.indexWhere(_.inBucket(now, dueDate))
                if(dueIndex == -1)  {
                  tasks += dueTask
                  true
                } else {
                  dueTasks(dueIndex) += dueTask
                  false
                }
              case None =>
                if (thisUserAssigned) tasks += AssignedTask.Plain(node.id, parentNode.id)
                true
            }
          } else false
        } else true
      } else deepSearch
    }

    AssignedTasks(dueTasks, tasks)
  }
}
