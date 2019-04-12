package wust.external.trello

import cats.Eval
import cats.kernel.instances.StringMonoid
import wust.ids._
import wust.graph._

import scala.collection.mutable

case class Card(
  id: String,
  name: String,
  desc: String,
  closed: Boolean,
  pos: Double,
  idLabels: List[String],
  idChecklists: List[String],
  idList: String
)

case class Membership(
  id: String,
  idMember: String
)

case class Prefs(
  permissionLevel: String,
  voting: String
)

case class CheckItem(
  id: String,
  name: String,
  pos: Double,
  state: String
)

case class CheckList(
  id: String,
  name: String,
  pos: Double,
  checkItems: List[CheckItem]
)

case class Column(
  id: String,
  name: String,
  closed: Boolean,
  pos: Double
)

case class IdRef(
  id: String
)

case class ActionData(
  card: Option[IdRef],
  board: Option[IdRef],
  list: Option[IdRef],
  text: Option[String]
)

case class Action(
  id: String,
  `type`: String,
  date: String,
  data: ActionData
)

case class Label(
  id: String,
  name: String,
  color: String,
)

case class Board(
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
  def translate(board: Board, currentTime: EpochMilli = EpochMilli.now)(nodeId: NodeId): GraphChanges = {
    val addNodes = mutable.Set.newBuilder[Node]
    val addEdges = mutable.Set.newBuilder[Edge]

    val boardNode = Node.Content(nodeId, NodeData.Markdown(board.name), NodeRole.Project, NodeMeta.default, Some(View.Kanban :: Nil))
    addNodes += boardNode

    // collect all labels in board
    val labelsById = new mutable.HashMap[String, NodeId]
    board.labels.foreach { label =>
      val content = if (label.name.isEmpty) label.color else label.name // TODO: user-defined colors
      val labelNode = Node.Content(NodeId.fresh, NodeData.Markdown(content), NodeRole.Tag, NodeMeta.default, None)
      addNodes += labelNode
      addEdges += Edge.Child(ParentId(boardNode.id), ChildId(labelNode.id))
      labelsById += label.id -> labelNode.id
    }

    // collect all checklists in board
    val checklistsById = new mutable.HashMap[String, NodeId]
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
    val listsById = new mutable.HashMap[String, NodeId]
    board.lists.foreach { list =>
      val listNode = Node.Content(NodeId.fresh, NodeData.Markdown(list.name), NodeRole.Stage, NodeMeta.default, Some(View.List :: Nil))
      addNodes += listNode
      val deletedAt = if (list.closed) Some(currentTime) else None // TODO: deletion time?
      val edgeData = EdgeData.Child(deletedAt = deletedAt, ordering = Some(BigDecimal(list.pos)))
      addEdges += Edge.Child(ParentId(boardNode.id), edgeData, ChildId(listNode.id))
      listsById += list.id -> listNode.id
    }

    // collect all cards in board
    val cardsById = new mutable.HashMap[String, NodeId]
    board.cards.foreach { card =>
      val cardNode = Node.Content(NodeId.fresh, NodeData.Markdown(card.name), NodeRole.Task, NodeMeta.default, Some(View.List :: Nil))
      addNodes += cardNode
      cardsById += card.id -> cardNode.id

      // attach description as property
      if (card.desc.nonEmpty) {
        val descNode = Node.Content(NodeId.fresh, NodeData.Markdown(card.desc), NodeRole.Neutral, NodeMeta.default, None)
        addNodes += descNode
        addEdges += Edge.LabeledProperty(cardNode.id, EdgeData.LabeledProperty.description, PropertyId(descNode.id))
      }

      // attach labels
      card.idLabels.foreach { idLabel =>
        labelsById.get(idLabel).foreach { nodeIdLabel =>
          addEdges += Edge.Child(ParentId(nodeIdLabel), ChildId(cardNode.id))
        }
      }

      // attach checklists
      card.idChecklists.zipWithIndex.foreach { case (idChecklist, idx) =>
        checklistsById.get(idChecklist).foreach { nodeIdChecklist =>
          addEdges += Edge.Child(ParentId(cardNode.id), EdgeData.Child(ordering = BigDecimal(idx)), ChildId(nodeIdChecklist))
        }
      }

      // attach to board and its stage
      val deletedAt = if (card.closed) Some(currentTime) else None // TODO: deletion time?
      val edgeData = EdgeData.Child(deletedAt = deletedAt, ordering = Some(BigDecimal(card.pos)))
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
                val commentNode = Node.Content(NodeId.fresh, NodeData.Markdown(text), NodeRole.Message, NodeMeta.default, None)
                addNodes += commentNode
                addEdges += Edge.Child(ParentId(nodeIdCard), ChildId(commentNode.id))
              }
            }
          }
        case _ => ()
      }
    }

    GraphChanges(
      addNodes = addNodes.result,
      addEdges = addEdges.result
    )
  }

  def decodeJson(json: String): Either[String, Board] = {
    import io.circe._
    import io.circe.generic.auto._
    import io.circe.parser._

    decode[Board](json) match {
      case Right(board) => Right(board)
      case Left(err)    => Left(s"Trello JSON is invalid: $err")
    }
  }
}
