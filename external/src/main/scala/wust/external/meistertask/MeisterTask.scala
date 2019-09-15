package wust.external.meistertask

import cats.Eval
import wust.graph._
import wust.ids._
import wust.util.collection.{BasicMap, eitherSeq}

import scala.collection.breakOut
import scala.util.{Failure, Success, Try}

final case class Task(
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

final case class CheckItem(
  checked: Boolean,
  name: String,
)

final case class Comment(
  name: String,
  author: String,
  date: EpochMilli
)

final case class Project(
  name: String,
  tasks: Seq[Task]
)

object MeisterTask {
  def translate(project: Project, currentTime: EpochMilli = EpochMilli.now): GraphChanges.Import = {
    val addNodes = Array.newBuilder[Node]
    val addEdges = Array.newBuilder[Edge]

    val tagsByName = BasicMap.ofString[NodeId]()
    val sectionsByName = BasicMap.ofString[NodeId]()
    val doneStageInSection = BasicMap.ofString[NodeId]()

    val projectNode = Node.Content(NodeId.fresh, NodeData.Markdown(project.name), NodeRole.Project, NodeMeta.default, NodeSchema.fromViews(View.Kanban.default))
    addNodes += projectNode

    project.tasks.foreach { task =>
      val views = if (task.notes.nonEmpty) View.List :: View.Chat :: View.Content :: Nil else View.List :: View.Chat :: Nil
      val taskNode = Node.Content(NodeId.fresh, NodeData.Markdown(task.name), NodeRole.Task, NodeMeta.default, NodeSchema.fromViewSeq(views))
      addNodes += taskNode

      // attach tags
      task.tags.foreach { tag =>
        val nodeIdTag = tagsByName.get(tag) match {
          case Some(nodeId) => nodeId
          case None =>
            val tagNode = Node.Content(NodeId.fresh, NodeData.Markdown(tag), NodeRole.Tag, NodeMeta.default, NodeSchema.empty)
            addNodes += tagNode
            addEdges += Edge.Child(ParentId(projectNode.id), ChildId(tagNode.id))
            tagsByName += tag -> tagNode.id
            tagNode.id
        }

        addEdges += Edge.Child(ParentId(nodeIdTag), ChildId(taskNode.id))
      }

      // attach notes
      task.notes.foreach { notes =>
        val notesNode = Node.Content(NodeId.fresh, NodeData.Markdown(notes), NodeRole.Note, NodeMeta.default, NodeSchema.empty)
        addNodes += notesNode
        addEdges += Edge.Child(ParentId(taskNode.id), ChildId(notesNode.id))
      }

      // attach comments
      task.comments.foreach { comment =>
        val commentNode = Node.Content(NodeId.fresh, NodeData.Markdown(comment.name), NodeRole.Message, NodeMeta.default, NodeSchema.empty)
        addNodes += commentNode
        addEdges += Edge.Child(ParentId(taskNode.id), ChildId(commentNode.id))
      }

      // create done column in this checklist
      val doneNode = Eval.later {
        val doneNode = Node.Content(NodeId.fresh, NodeData.Markdown(Graph.doneText), NodeRole.Stage, NodeMeta.default, NodeSchema.empty)
        addNodes += doneNode
        addEdges += Edge.Child(ParentId(taskNode.id), ChildId(doneNode.id))
        doneNode
      }

      // attach checklist items
      task.checklists.foreach { checkItem =>
        val checkItemNode = Node.Content(NodeId.fresh, NodeData.Markdown(checkItem.name), NodeRole.Task, NodeMeta.default, NodeSchema.empty)
        addNodes += checkItemNode
        addEdges += Edge.Child(ParentId(taskNode.id), ChildId(checkItemNode.id))
        if (checkItem.checked) addEdges += Edge.Child(ParentId(doneNode.value.id), ChildId(checkItemNode.id))
      }

      // attach custom fields
      task.customFields.foreach { comment =>
        // TODO: how is it encoded?
      }

      task.dueDate.foreach { date =>
        val dateNode = Node.Content(NodeId.fresh, NodeData.DateTime(DateTimeMilli(date)), NodeRole.Neutral, NodeMeta.default, NodeSchema.empty)
        addNodes += dateNode
        addEdges += Edge.LabeledProperty(taskNode.id, EdgeData.LabeledProperty.dueDate, PropertyId(dateNode.id))
      }


      // attach task to board
      addEdges += Edge.Child(ParentId(projectNode.id), ChildId(taskNode.id))

      // attach task to stage
      val nodeIdSection = sectionsByName.get(task.section) match {
        case Some(nodeId) => nodeId
        case None =>
          val sectionNode = Node.Content(NodeId.fresh, NodeData.Markdown(task.section), NodeRole.Stage, NodeMeta.default, NodeSchema.empty)
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
              val doneNode = Node.Content(NodeId.fresh, NodeData.Markdown(Graph.doneText), NodeRole.Stage, NodeMeta.default, NodeSchema.empty)
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

  object RegexDecoders {
    import kantan.regex._
    import kantan.regex.implicits._

    implicit val dateMatch: GroupDecoder[EpochMilli] = GroupDecoder[String].emap(str => EpochMilli.parse(str).toRight(DecodeError.TypeError("Is not a Date")))

    val commentRegex = Regex[(String, EpochMilli, String)](rx"([^(]*) \(([^)]*)\): (.*)").map(_.map { case (author, date, content) =>
      Comment(name = content, author = author, date = date)
    })

    val checkItemRegex = Regex[(String, String)](rx"\[( |x)\] ([^\[]*)").map(_.map { case (check, content) =>
      CheckItem(checked = check == "x", name = content)
    })
  }

  def decodeCSV(projectName: String, csv: String): Either[String, Project] = {
    import RegexDecoders._
    import kantan.csv._
    import kantan.csv.ops._

    implicit val commentCodec: CellDecoder[Comment] = CellDecoder.from { str =>
      commentRegex.eval(str).toList.headOption.toRight(DecodeError.TypeError(s"Cannot extract comment section from '$str'")).flatMap {
        case Right(value) => Right(value)
        case Left(err) => Left(DecodeError.TypeError(err.getMessage))
      }
    }
    implicit val checklistCodec: CellDecoder[Seq[CheckItem]] = CellDecoder.from { str =>
      eitherSeq(checkItemRegex.eval(str).toList.map {
        case Right(value) => Right(value)
        case Left(err) => Left(DecodeError.TypeError(err.getMessage))
      }).left.map(_.head)
    }
    implicit val dateCodec: CellDecoder[EpochMilli] = CellDecoder.from(str => EpochMilli.parse(str).toRight(DecodeError.TypeError("Is not a Date")))
    implicit def listCodec[T: CellDecoder]: CellDecoder[Seq[T]] = CellDecoder.from { str =>
      if (str.isEmpty) Right(Nil) else eitherSeq(str.split(";").map(CellDecoder[T].decode)(breakOut)).left.map(_.head)
    }
    implicit val taskDecoder: HeaderDecoder[Task] = HeaderDecoder.decoder("id","token","name","notes","created_at","updated_at","status","due_date","status_updated_at","assignee","section","tags","custom_fields","checklists","comments")(Task.apply)
    val config = CsvConfiguration.rfc.copy(header = CsvConfiguration.Header.Implicit)
    Try(eitherSeq(csv.asCsvReader[Task](config).toList)) match { // TODO: try because kantan.csv can sometimes on invalid headers. report or fix...
      case Success(Right(tasks)) => Right(Project(projectName, tasks))
      case Success(Left(err)) => Left(s"MeisterTask CSV is invalid: $err")
      case Failure(t) => Left(s"MeisterTask CSV is invalid: unexpected content")
    }
  }
}
