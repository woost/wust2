package wust.webApp.dragdrop

import io.circe._
import io.circe.generic.semiauto._
import wust.ids.NodeId

sealed trait DragPayload extends Product
sealed trait DragTarget extends Product
object DragItem extends wust.ids.serialize.Circe {
  case object DisableDrag extends DragPayload

  sealed trait AnyNodes extends DragPayload with DragTarget { def nodeIds: Seq[NodeId] }
  sealed trait SingleNode extends AnyNodes { def nodeId: NodeId; def nodeIds: Seq[NodeId] = nodeId :: Nil }
  sealed trait ChildNode extends SingleNode
  sealed trait ParentNode extends SingleNode
  sealed trait MultiParentNodes extends AnyNodes

  case class Tag(nodeId: NodeId) extends ParentNode
  case class Channel(nodeId: NodeId) extends ParentNode
  case class SelectedNode(nodeId: NodeId) extends SingleNode
  case class SelectedNodes(nodeIds: Seq[NodeId]) extends DragPayload
  case object SelectedNodesBar extends DragTarget

  object Chat {
    case class Messages(nodeIds: Seq[NodeId]) extends AnyNodes
    case class Message(nodeId: NodeId) extends ChildNode
    case class Thread(nodeIds: Seq[NodeId]) extends MultiParentNodes
    case class Page(nodeIds: Seq[NodeId]) extends MultiParentNodes
  }

  object Kanban {
    sealed trait Item { def nodeId: NodeId }
    case class Column(nodeId: NodeId) extends ParentNode with Item
    case class Card(nodeId: NodeId) extends ChildNode with Item
  }

  val payloadPropName = "_wust_dragpayload"
  val targetPropName = "_wust_dragtarget"

  implicit val payloadDecoder: Decoder[DragPayload] = deriveDecoder[DragPayload]
  implicit val payloadEncoder: Encoder[DragPayload] = deriveEncoder[DragPayload]

  implicit val targetDecoder: Decoder[DragTarget] = deriveDecoder[DragTarget]
  implicit val targetEncoder: Encoder[DragTarget] = deriveEncoder[DragTarget]
}

sealed trait DragContainer
object DragContainer extends wust.ids.serialize.Circe {
  object Kanban {
    sealed trait Area extends DragContainer { def parentIds: Seq[NodeId] }
    case class Column(nodeId:NodeId) extends Area { def parentIds = nodeId :: Nil }
    case class ColumnArea(parentIds:Seq[NodeId]) extends Area
    case class Uncategorized(parentIds:Seq[NodeId]) extends Area
  }

  val propName = "_wust_dragcontainer"

  implicit val decoder: Decoder[DragContainer] = deriveDecoder[DragContainer]
  implicit val encoder: Encoder[DragContainer] = deriveEncoder[DragContainer]
}

