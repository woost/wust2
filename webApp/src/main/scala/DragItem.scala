package wust.webApp

import io.circe._
import io.circe.generic.semiauto._
import wust.ids.NodeId

sealed trait DragPayload
sealed trait DragTarget
object DragItem extends wust.ids.serialize.Circe {
  sealed trait AnyNodes extends DragPayload with DragTarget { def nodeIds: Seq[NodeId] }
  sealed trait SingleNode extends AnyNodes { def nodeId: NodeId; def nodeIds: Seq[NodeId] = nodeId :: Nil }
  sealed trait ChildNode extends SingleNode
  sealed trait ParentNode extends SingleNode

  case class ChatMsg(nodeId: NodeId) extends ChildNode
  case class Tag(nodeId: NodeId) extends ParentNode
  case class Channel(nodeId: NodeId) extends ParentNode
  case class SelectedNodes(nodeIds: Seq[NodeId]) extends DragPayload
  case object SelectedNodesBar extends DragTarget

  sealed trait KanbanItem { def nodeId:NodeId }
  case class KanbanColumn(nodeId: NodeId) extends ParentNode with KanbanItem
  case class KanbanCard(nodeId: NodeId) extends ChildNode with KanbanItem

  val payloadAttrName = "data-dragpayload"
  val targetAttrName = "data-dragtarget"

  implicit val payloadDecoder: Decoder[DragPayload] = deriveDecoder[DragPayload]
  implicit val payloadEncoder: Encoder[DragPayload] = deriveEncoder[DragPayload]

  implicit val targetDecoder: Decoder[DragTarget] = deriveDecoder[DragTarget]
  implicit val targetEncoder: Encoder[DragTarget] = deriveEncoder[DragTarget]
}

sealed trait DragContainer
object DragContainer extends wust.ids.serialize.Circe {
  case class KanbanColumn(nodeId:NodeId) extends DragContainer
  case class Page(parentIds:Seq[NodeId]) extends DragContainer

  val attrName = "data-dragcontainer"

  implicit val decoder: Decoder[DragContainer] = deriveDecoder[DragContainer]
  implicit val encoder: Encoder[DragContainer] = deriveEncoder[DragContainer]
}

