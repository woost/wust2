package wust.webApp

import wust.ids.NodeId

sealed trait DragPayload
sealed trait DragTarget
sealed trait AnyNodes extends DragPayload with DragTarget { def nodeIds: Seq[NodeId] }
sealed trait SingleNode extends AnyNodes { def nodeId: NodeId; def nodeIds: Seq[NodeId] = nodeId :: Nil }
sealed trait ChildNode extends SingleNode
sealed trait ParentNode extends SingleNode
object DragItem extends wust.ids.serialize.Circe {
  case class ChatMsg(nodeId: NodeId) extends ChildNode
  case class Tag(nodeId: NodeId) extends ParentNode
  case class Channel(nodeId: NodeId) extends ParentNode
  case class SelectedNodes(nodeIds: Seq[NodeId]) extends DragPayload
  case object SelectedNodesBar extends DragTarget
  case class KanbanColumn(nodeId: NodeId) extends ParentNode
  case class KanbanCard(nodeId: NodeId) extends ChildNode

  val payloadAttrName = "data-dragpayload"
  val targetAttrName = "data-dragtarget"

  import io.circe._
  import io.circe.generic.semiauto._
  implicit val payloadDecoder: Decoder[DragPayload] = deriveDecoder[DragPayload]
  implicit val payloadEncoder: Encoder[DragPayload] = deriveEncoder[DragPayload]

  implicit val targetDecoder: Decoder[DragTarget] = deriveDecoder[DragTarget]
  implicit val targetEncoder: Encoder[DragTarget] = deriveEncoder[DragTarget]
}

