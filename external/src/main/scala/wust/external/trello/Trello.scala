package wust.external.trello

import cats.Eval
import wust.graph._
import wust.ids._
import wust.util.collection._

final case class Card(
  id: String,
  name: String,
  desc: String,
  closed: Boolean,
  pos: Double,
  idLabels: List[String],
  idChecklists: List[String],
  idList: String,
  due: Option[EpochMilli],
  dueComplete: Boolean
)

final case class Membership(
  id: String,
  idMember: String
)

final case class Prefs(
  permissionLevel: String,
  voting: String
)

final case class CheckItem(
  id: String,
  name: String,
  pos: Double,
  state: String
)

final case class CheckList(
  id: String,
  name: String,
  pos: Double,
  checkItems: List[CheckItem]
)

final case class Column(
  id: String,
  name: String,
  closed: Boolean,
  pos: Double
)

final case class IdRef(
  id: String
)

final case class MemberCreator(
  fullName: Option[String],
  username: Option[String]
)

final case class ActionData(
  card: Option[IdRef],
  board: Option[IdRef],
  list: Option[IdRef],
  text: Option[String]
)

final case class Action(
  id: String,
  `type`: String,
  date: String,
  data: ActionData,
  memberCreator: Option[MemberCreator]
)

final case class Label(
  id: String,
  name: String,
  color: String,
)

final case class Board(
  id: String,
  name: String,
  closed: Boolean,
  prefs: Prefs,
  memberships: List[Membership],
  actions: List[Action],
  cards: List[Card],
  lists: List[Column],
  labels: List[Label],
  checklists: List[CheckList]
)

object Trello {
  def translate(board: Board, currentTime: EpochMilli = EpochMilli.now): GraphChanges.Import = {
    val addNodes = Array.newBuilder[Node]
    val addEdges = Array.newBuilder[Edge]

    val labelsById = BasicMap.ofString[NodeId]()
    val checklistsById = BasicMap.ofString[NodeId]()
    val listsById = BasicMap.ofString[NodeId]()
    val cardsById = BasicMap.ofString[NodeId]()

    val boardNode = Node.Content(NodeId.fresh, NodeData.Markdown(board.name), NodeRole.Project, NodeMeta.default, Some(View.Kanban :: Nil))
    addNodes += boardNode

    // collect all labels in board
    board.labels.foreach { label =>
      val content = if (label.name.isEmpty) label.color else label.name // TODO: user-defined colors
      val labelNode = Node.Content(NodeId.fresh, NodeData.Markdown(content), NodeRole.Tag, NodeMeta.default, None)
      addNodes += labelNode
      addEdges += Edge.Child(ParentId(boardNode.id), ChildId(labelNode.id))
      labelsById += label.id -> labelNode.id
    }

    // collect all checklists in board
    board.checklists.foreach { checklist =>
      val checklistNode = Node.Content(NodeId.fresh, NodeData.Markdown(checklist.name), NodeRole.Task, NodeMeta.default, Some(View.List :: Nil))
      addNodes += checklistNode
      checklistsById += checklist.id -> checklistNode.id

      // create done column in this checklist
      val doneNode = Eval.later {
        val doneNode = Node.Content(NodeId.fresh, NodeData.Markdown(Graph.doneText), NodeRole.Stage, NodeMeta.default, None)
        addNodes += doneNode
        addEdges += Edge.Child(ParentId(checklistNode.id), ChildId(doneNode.id))
        doneNode
      }


      // collect all items in this checklist
      checklist.checkItems.foreach { checkItem =>
        val checkitemNode = Node.Content(NodeId.fresh, NodeData.Markdown(checkItem.name), NodeRole.Task, NodeMeta.default, None)
        addNodes += checkitemNode

        // add item to checklist and potentially its done stage
        addEdges += Edge.Child(ParentId(checklistNode.id), EdgeData.Child(ordering = BigDecimal(checkItem.pos)), ChildId(checkitemNode.id))
        if (checkItem.state == "complete") addEdges += Edge.Child(ParentId(doneNode.value.id), EdgeData.Child(ordering = BigDecimal(checkItem.pos)), ChildId(checkitemNode.id))
      }
    }

    // collect all lists/columns in board
    board.lists.foreach { list =>
      val listNode = Node.Content(NodeId.fresh, NodeData.Markdown(list.name), NodeRole.Stage, NodeMeta.default, Some(View.List :: Nil))
      addNodes += listNode
      val deletedAt = if (list.closed) Some(currentTime) else None // TODO: deletion time?
      val edgeData = EdgeData.Child(deletedAt = deletedAt, ordering = BigDecimal(list.pos))
      addEdges += Edge.Child(ParentId(boardNode.id), edgeData, ChildId(listNode.id))
      listsById += list.id -> listNode.id
    }

    // collect all cards in board
    board.cards.foreach { card =>
      val views = if (card.desc.nonEmpty) View.List :: View.Chat :: View.Content :: Nil else View.List :: View.Chat :: Nil
      val cardNode = Node.Content(NodeId.fresh, NodeData.Markdown(card.name), NodeRole.Task, NodeMeta.default, Some(views))
      addNodes += cardNode
      cardsById += card.id -> cardNode.id

      // attach due date
      card.due.foreach { date =>
        val dateNode = Node.Content(NodeId.fresh, NodeData.DateTime(DateTimeMilli(date), end = None), NodeRole.Neutral, NodeMeta.default, None)
        addNodes += dateNode
        addEdges += Edge.LabeledProperty(cardNode.id, EdgeData.LabeledProperty.dueDate, PropertyId(dateNode.id))
      }

      // attach description as note
      if (card.desc.nonEmpty) {
        val descNode = Node.Content(NodeId.fresh, NodeData.Markdown(card.desc), NodeRole.Note, NodeMeta.default, None)
        addNodes += descNode
        addEdges += Edge.Child(ParentId(cardNode.id), ChildId(descNode.id))
      }

      // attach labels
      card.idLabels.foreach { idLabel =>
        labelsById.get(idLabel).foreach { nodeIdLabel =>
          addEdges += Edge.Child(ParentId(nodeIdLabel), ChildId(cardNode.id))
        }
      }

      // attach checklists
      card.idChecklists.foreachWithIndex { (idx, idChecklist) =>
        checklistsById.get(idChecklist).foreach { nodeIdChecklist =>
          addEdges += Edge.Child(ParentId(cardNode.id), EdgeData.Child(ordering = BigDecimal(idx)), ChildId(nodeIdChecklist))
        }
      }

      // attach to board and its stage
      val deletedAt = if (card.closed) Some(currentTime) else None // TODO: deletion time?
      val edgeData = EdgeData.Child(deletedAt = deletedAt, ordering = BigDecimal(card.pos))
      addEdges += Edge.Child(ParentId(boardNode.id), edgeData, ChildId(cardNode.id))
      listsById.get(card.idList).foreach { nodeIdList =>
        addEdges += Edge.Child(ParentId(nodeIdList), edgeData, ChildId(cardNode.id))
      }
    }

    // collect all card-comments in board and attach them to the cards
    //TODO: reverse for ordering. better: order comments by date.
    board.actions.reverse.foreach { action =>
      action.`type` match {
        case "commentCard" =>
          action.data.text.foreach { text =>
            action.data.card.foreach { card =>
              cardsById.get(card.id).foreach { nodeIdCard =>
                val authorPrefix = action.memberCreator.flatMap(member => member.fullName.orElse(member.username)).fold("")(author => s"${author}: ")
                val commentNode = Node.Content(NodeId.fresh, NodeData.Markdown(s"$authorPrefix$text"), NodeRole.Message, NodeMeta.default, None)
                addNodes += commentNode
                addEdges += Edge.Child(ParentId(nodeIdCard), ChildId(commentNode.id))
              }
            }
          }
        case _ => ()
      }
    }

    GraphChanges.Import(
      GraphChanges(
        addNodes = addNodes.result,
        addEdges = addEdges.result
      ),
      topLevelNodeIds = List(boardNode.id),
      focusNodeId = Some(boardNode.id)
    )
  }

  def decodeJson(json: String): Either[String, Board] = {
    import io.circe._
    import io.circe.generic.auto._
    import io.circe.parser._
    import wust.external.Circe._

    decode[Board](json) match {
      case Right(board) => Right(board)
      case Left(err)    => Left(s"Trello JSON is invalid: $err")
    }
  }
}
