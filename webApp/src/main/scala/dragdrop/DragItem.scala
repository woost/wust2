package wust.webApp.dragdrop

import io.circe._
import io.circe.generic.semiauto._
import wust.ids.{NodeId, UserId}

sealed trait DragPayload extends Product
sealed trait DragTarget extends Product
object DragItem extends wust.ids.serialize.Circe {
  case object DisableDrag extends DragPayload

  sealed trait AnyNodes extends DragPayload with DragTarget { def nodeIds: Iterable[NodeId] }
  sealed trait SingleNode extends AnyNodes { def nodeId: NodeId; @inline def nodeIds: Iterable[NodeId] = nodeId :: Nil }
  sealed trait ChildNode extends SingleNode
  sealed trait ParentNode extends SingleNode
  sealed trait MultiParentNodes extends AnyNodes

  case class Tag(nodeId: NodeId) extends ParentNode
  case class Channel(nodeId: NodeId) extends ParentNode
  case class AvatarNode(userId: UserId) extends SingleNode { @inline def nodeId = userId.asInstanceOf[NodeId] }
  case class SelectedNode(nodeId: NodeId) extends SingleNode
  case class SelectedNodes(nodeIds: Seq[NodeId]) extends DragPayload

  object Chat {
    case class Messages(nodeIds: Seq[NodeId]) extends AnyNodes
    case class Message(nodeId: NodeId) extends ChildNode
    case class Thread(nodeIds: Seq[NodeId]) extends MultiParentNodes
    case class Page(nodeId: NodeId) extends ParentNode
  }

  object Kanban {
    sealed trait Item { def nodeId: NodeId }
    case class Column(nodeId: NodeId) extends ParentNode with Item
    case class Card(nodeId: NodeId) extends ChildNode with Item
  }

  case object Sidebar extends DragTarget

  val payloadPropName = "_wust_dragpayload"
  val targetPropName = "_wust_dragtarget"
  val disableSortPropName = "_wust_disableSort"
  val draggedActionPropName = "_wust_draggedaction"

  implicit val payloadDecoder: Decoder[DragPayload] = deriveDecoder[DragPayload]
  implicit val payloadEncoder: Encoder[DragPayload] = deriveEncoder[DragPayload]

  implicit val targetDecoder: Decoder[DragTarget] = deriveDecoder[DragTarget]
  implicit val targetEncoder: Encoder[DragTarget] = deriveEncoder[DragTarget]
}

sealed trait DragContainer { def parentId: NodeId; def items: Seq[NodeId] }
object DragContainer extends wust.ids.serialize.Circe {
  object Kanban {
    sealed trait AreaForColumns extends DragContainer
    sealed trait AreaForCards extends DragContainer
    case class Column(nodeId:NodeId, items: Seq[NodeId]) extends AreaForColumns with AreaForCards { @inline def parentId = nodeId }
    case class ColumnArea(parentId:NodeId, items: Seq[NodeId]) extends AreaForColumns
    case class Inbox(parentId:NodeId, items: Seq[NodeId]) extends AreaForCards
  }

  // Fixme: items workaround. Differentiate what is parent and what are the items
  case class AvatarHolder(nodeId: NodeId) extends DragContainer { @inline def parentId = nodeId; @inline def items = Seq(nodeId); }

  val propName = "_wust_dragcontainer"

  implicit val decoder: Decoder[DragContainer] = deriveDecoder[DragContainer]
  implicit val encoder: Encoder[DragContainer] = deriveEncoder[DragContainer]
}

