package wust.external.wunderlist

import cats.Eval
import wust.graph.{Edge, Graph, GraphChanges, Node, NodeMeta}
import wust.ids._
import wust.util.collection._

import scala.collection.{breakOut, mutable}

object Wunderlist {
  type Id = Long

  case class Account(
    user: Id,
    exported: EpochMilli,
    data: Data
  )

  case class Data(
    lists: List[ListItem],
    tasks: List[TaskItem],
    reminders: List[ReminderItem],
    subtasks: List[SubTaskItem],
    notes: List[NoteItem],
    task_positions: List[TaskPosition],
    subtask_positions: List[TaskPosition]
  )

  case class ListItem(
    id: Id,
    title: String,
    owner_type: String,
    owner_id: Id,
    list_type: String, // inbox | list | ???
    public: Boolean,
    revision: Int,
    created_at: EpochMilli
  )

  case class TaskItem(
    id: Id,
    created_at: EpochMilli,
    created_by_id: Id,
    created_by_request_id: String,
    completed: Boolean,
    completed_at: Option[EpochMilli],
    completed_by_id: Option[Id],
    due_date: Option[EpochMilli],
    starred: Boolean,
    list_id: Id,
    revision: Int,
    title: String,
  )

  case class SubTaskItem(
    id: Id,
    task_id: Id,
    completed: Boolean,
    completed_at: Option[EpochMilli],
    completed_by: Option[String],
    created_at: EpochMilli,
    created_by_id: Id,
    created_by_request_id: String,
    revision: Int,
    title: String
  )

  case class TaskPosition(
    id: Id,
    revision: Int,
    values: List[Id]
  )

  case class NoteItem(
    id: Id,
    revision: Int,
    content: String,
    task_id: Id,
    created_by_request_id: String
  )

  //TODO
  case class ReminderItem()

  def translate(account: Account, currentTime: EpochMilli = EpochMilli.now): GraphChanges.Import = {
    case class NodeInfo(nodeId: NodeId, doneStageId: Eval[NodeId])

    val addNodes = Array.newBuilder[Node]
    val addEdges = Array.newBuilder[Edge]
    val topLevelNodeIds = mutable.ArrayBuffer[NodeId]()

    val listById = mutable.HashMap[Id, NodeInfo]()
    val taskById = mutable.HashMap[Id, NodeInfo]()

    def getDoneNode(parentId: NodeId): NodeId = {
      val doneNode = Node.Content(NodeId.fresh, NodeData.Markdown(Graph.doneText), NodeRole.Stage, NodeMeta.default, None)
      addNodes += doneNode
      addEdges += Edge.Child(ParentId(parentId), ChildId(doneNode.id))
      doneNode.id
    }

    account.data.lists.foreach { list =>
      val projectNode = Node.Content(NodeId.fresh, NodeData.Markdown(list.title), NodeRole.Project, NodeMeta.default, Some(View.List :: Nil))
      listById += list.id -> NodeInfo(projectNode.id, Eval.later(getDoneNode(projectNode.id)))
      topLevelNodeIds += projectNode.id
      addNodes += projectNode
    }

    val taskPositionMap: mutable.HashMap[Id, Int] = account.data.task_positions.mapWithIndex((idx, pos) => pos.id -> (idx + 1))(breakOut)
    for {
      task <- account.data.tasks
      listInfo <- listById.get(task.list_id)
      ordering = taskPositionMap.getOrElse(task.id, 0)
    } {
      //TODO: define notes view if notes available on task
      val taskNode = Node.Content(NodeId.fresh, NodeData.Markdown(task.title), NodeRole.Task, NodeMeta.default, Some(View.List :: View.Chat :: Nil))
      taskById += task.id -> NodeInfo(taskNode.id, Eval.later(getDoneNode(taskNode.id)))
      addNodes += taskNode
      val childData = EdgeData.Child(ordering = BigDecimal(ordering))
      addEdges += Edge.Child(ParentId(listInfo.nodeId), childData, ChildId(taskNode.id))
      if (task.completed) {
        addEdges += Edge.Child(ParentId(listInfo.doneStageId.value), childData, ChildId(taskNode.id))
      }
      task.due_date.foreach { date =>
        val dateNode = Node.Content(NodeId.fresh, NodeData.DateTime(DateTimeMilli(date)), NodeRole.Neutral, NodeMeta.default, None)
        addNodes += dateNode
        addEdges += Edge.LabeledProperty(taskNode.id, EdgeData.LabeledProperty.dueDate, PropertyId(dateNode.id))
      }
    }

    val subTaskPositionMap: mutable.HashMap[Id, Int] = account.data.subtask_positions.mapWithIndex((idx, pos) => pos.id -> idx)(breakOut)
    for {
      task <- account.data.subtasks
      taskInfo <- taskById.get(task.task_id)
      ordering = subTaskPositionMap.getOrElse(task.id, 0)
    } {
      val taskNode = Node.Content(NodeId.fresh, NodeData.Markdown(task.title), NodeRole.Task, NodeMeta.default, Some(View.List :: View.Chat :: Nil))
      addNodes += taskNode
      val childData = EdgeData.Child(ordering = BigDecimal(ordering))
      addEdges += Edge.Child(ParentId(taskInfo.nodeId), childData, ChildId(taskNode.id))
      if (task.completed) {
        addEdges += Edge.Child(ParentId(taskInfo.doneStageId.value), childData, ChildId(taskNode.id))
      }
    }

    for {
      note <- account.data.notes
      taskInfo <- taskById.get(note.task_id)
    } {
      val notesNode = Node.Content(NodeId.fresh, NodeData.Markdown(note.content), NodeRole.Note, NodeMeta.default, None)
      addNodes += notesNode
      addEdges += Edge.Child(ParentId(taskInfo.nodeId), ChildId(notesNode.id))
    }

    GraphChanges.Import(
      GraphChanges(
        addNodes = addNodes.result,
        addEdges = addEdges.result
      ),
      topLevelNodeIds = topLevelNodeIds,
      focusNodeId = None
    )
  }

  def decodeJson(json: String): Either[String, Account] = {
    import io.circe._
    import io.circe.generic.auto._
    import io.circe.parser._
    import wust.external.Circe._

    decode[Account](json) match {
      case Right(account) => Right(account)
      case Left(err)    => Left(s"Wunderlist JSON is invalid: $err")
    }
  }
}
