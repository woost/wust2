package meistertask

import java.util.Date

import cats.Eval
import wust.graph._
import wust.ids._
import wust.util.StringOps
import wust.util.collection.eitherSeq

import scala.collection.{breakOut, mutable}
import scala.util.{Failure, Success, Try}

case class Task(
  id: String,
  token: String,
  name: String,
  notes: Option[String],
  createdAt: EpochMilli,
  updatedAt: EpochMilli,
  status: Int,
  dueDate: Option[EpochMilli],
  statusUpdatedAt: EpochMilli,
  assignee: Option[String],
  section: String,
  tags: Seq[String],
  customFields: Option[String],
  checklists: Seq[CheckItem],
  comments: Seq[Comment]
)

case class CheckItem(
  checked: Boolean,
  name: String,
)

case class Comment(
  name: String,
  author: String,
  date: EpochMilli
)

case class Project(
  name: String,
  tasks: Seq[Task]
)

object MeisterTask {
  def translate(project: Project, currentTime: EpochMilli = EpochMilli.now): GraphChanges.Import = {
    val addNodes = mutable.Set.newBuilder[Node]
    val addEdges = mutable.Set.newBuilder[Edge]

    val projectNode = Node.Content(NodeId.fresh, NodeData.Markdown(project.name), NodeRole.Project, NodeMeta.default, Some(View.Kanban :: Nil))
    addNodes += projectNode

    val tagsByName = new mutable.HashMap[String, NodeId]
    val sectionsByName = new mutable.HashMap[String, NodeId]
    val doneStageInSection = new mutable.HashMap[String, NodeId]
    project.tasks.foreach { task =>
      val taskNode = Node.Content(NodeId.fresh, NodeData.Markdown(task.name), NodeRole.Task, NodeMeta.default, Some(View.List :: Nil))
      addNodes += taskNode

      // attach tags
      task.tags.foreach { tag =>
        val nodeIdTag = tagsByName.get(tag) match {
          case Some(nodeId) => nodeId
          case None =>
            val tagNode = Node.Content(NodeId.fresh, NodeData.Markdown(tag), NodeRole.Tag, NodeMeta.default, None)
            addNodes += tagNode
            addEdges += Edge.Child(ParentId(projectNode.id), ChildId(tagNode.id))
            tagsByName += tag -> tagNode.id
            tagNode.id
        }

        addEdges += Edge.Child(ParentId(nodeIdTag), ChildId(taskNode.id))
      }

      // attach notes as description
      task.notes.foreach { notes =>
        val notesNode = Node.Content(NodeId.fresh, NodeData.Markdown(notes), NodeRole.Neutral, NodeMeta.default, None)
        addNodes += notesNode
        addEdges += Edge.LabeledProperty(taskNode.id, EdgeData.LabeledProperty.description, PropertyId(notesNode.id))
      }

      // attach comments
      task.comments.foreach { comment =>
        val commentNode = Node.Content(NodeId.fresh, NodeData.Markdown(comment.name), NodeRole.Message, NodeMeta.default, None)
        addNodes += commentNode
        addEdges += Edge.Child(ParentId(taskNode.id), ChildId(commentNode.id))
      }

      // create done column in this checklist
      val doneNode = Eval.later {
        val doneNode = Node.Content(NodeId.fresh, NodeData.Markdown(Graph.doneText), NodeRole.Stage, NodeMeta.default, None)
        addNodes += doneNode
        addEdges += Edge.Child(ParentId(taskNode.id), ChildId(doneNode.id))
        doneNode
      }

      // attach checklist items
      task.checklists.foreach { checkItem =>
        val checkItemNode = Node.Content(NodeId.fresh, NodeData.Markdown(checkItem.name), NodeRole.Task, NodeMeta.default, None)
        addNodes += checkItemNode
        addEdges += Edge.Child(ParentId(taskNode.id), ChildId(checkItemNode.id))
        if (checkItem.checked) addEdges += Edge.Child(ParentId(doneNode.value.id), ChildId(checkItemNode.id))
      }

      // attach custom fields
      task.customFields.foreach { comment =>
        // TODO: how is it encoded?
      }

      task.dueDate.foreach { date =>
        val dateNode = Node.Content(NodeId.fresh, NodeData.Date(date), NodeRole.Neutral, NodeMeta.default, None)
        addNodes += dateNode
        addEdges += Edge.LabeledProperty(taskNode.id, EdgeData.LabeledProperty.dueDate, PropertyId(dateNode.id))
      }

      // attach task to board and stage
      val nodeIdSection = sectionsByName.get(task.section) match {
        case Some(nodeId) => nodeId
        case None =>
          val sectionNode = Node.Content(NodeId.fresh, NodeData.Markdown(task.section), NodeRole.Stage, NodeMeta.default, None)
          addNodes += sectionNode
          addEdges += Edge.Child(ParentId(projectNode.id), ChildId(sectionNode.id))
          sectionsByName += task.section -> sectionNode.id
          sectionNode.id
      }

      task.status match {
        case 2 => // 2 - completed
          val doneStageId = doneStageInSection.get(task.section) match {
            case Some(nodeId) => nodeId
            case None =>
              val doneNode = Node.Content(NodeId.fresh, NodeData.Markdown(Graph.doneText), NodeRole.Stage, NodeMeta.default, None)
              addNodes += doneNode
              addEdges += Edge.Child(ParentId(nodeIdSection), ChildId(doneNode.id))
              doneStageInSection += task.section -> doneNode.id
              doneNode.id
          }

          addEdges += Edge.Child(ParentId(doneStageId), ChildId(taskNode.id))
        case 18 => // 18 - archived
          addEdges += Edge.Child.delete(ParentId(nodeIdSection), currentTime, ChildId(taskNode.id))
        case _ => // 1 - open
          addEdges += Edge.Child(ParentId(nodeIdSection), ChildId(taskNode.id))
      }

    }

    GraphChanges.Import(
      GraphChanges(
        addNodes = addNodes.result,
        addEdges = addEdges.result
      ),
      topLevelNodeIds = List(projectNode.id),
      focusNodeId = Some(projectNode.id)
    )
  }

  private def convertToEpochMilli(str: String) = {
    StringOps.safeToDate(str) match {
      case Some(date) => Right(EpochMilli(date.getTime))
      case None => Left("Is not a Date")
    }
  }

  object RegexDecoders {
    import kantan.regex._
    import kantan.regex.ops._
    import kantan.regex.implicits._

    implicit val dateMatch: GroupDecoder[EpochMilli] = GroupDecoder[String].emap(convertToEpochMilli(_).left.map(DecodeError.TypeError(_)))

    val commentRegex = Regex[(String, EpochMilli, String)](rx"([^(]*) \(([^)]*)\): (.*)").map(_.map { case (author, date, content) =>
      meistertask.Comment(name = content, author = author, date = date)
    })

    val checkItemRegex = Regex[(String, String)](rx"\[( |x)\] ([^\[]*)").map(_.map { case (check, content) =>
      meistertask.CheckItem(checked = check == "x", name = content)
    })
  }

  def decodeCSV(projectName: String, csv: String): Either[String, Project] = {
    import kantan.csv._
    import kantan.csv.ops._
    import kantan.csv.generic._
    import RegexDecoders._

    implicit val commentCodec: CellDecoder[meistertask.Comment] = CellDecoder.from { str =>
      commentRegex.eval(str).toList.headOption.toRight(DecodeError.TypeError(s"Cannot extract comment section from '$str'")).flatMap {
        case Right(value) => Right(value)
        case Left(err) => Left(DecodeError.TypeError(err.getMessage))
      }
    }
    implicit val checklistCodec: CellDecoder[Seq[meistertask.CheckItem]] = CellDecoder.from { str =>
      eitherSeq(checkItemRegex.eval(str).toList.map {
        case Right(value) => Right(value)
        case Left(err) => Left(DecodeError.TypeError(err.getMessage))
      }).left.map(_.head)
    }
    implicit val dateCodec: CellDecoder[EpochMilli] = CellDecoder.from(convertToEpochMilli(_).left.map(DecodeError.TypeError(_)))
    implicit def listCodec[T: CellDecoder]: CellDecoder[Seq[T]] = CellDecoder.from { str =>
      if (str.isEmpty) Right(Nil) else eitherSeq(str.split(";").map(CellDecoder[T].decode)(breakOut)).left.map(_.head)
    }
    implicit val taskDecoder: HeaderDecoder[Task] = HeaderDecoder.decoder("id","token","name","notes","created_at","updated_at","status","due_date","status_updated_at","assignee","section","tags","custom_fields","checklists","comments")(Task.apply)
    val config = CsvConfiguration.rfc.copy(header = CsvConfiguration.Header.Implicit)
    Try(eitherSeq(csv.asCsvReader[meistertask.Task](config).toList)) match { // TODO: try because kantan.csv can sometimes on invalid headers. report or fix...
      case Success(Right(tasks)) => Right(Project(projectName, tasks))
      case Success(Left(err)) => Left(s"MeisterTask CSV is invalid: $err")
      case Failure(t) => Left(s"MeisterTask CSV is invalid: unexpected content")
    }
  }
}
