package wust.webApp.dragdrop

import wust.ids.{NodeId, UserId}

sealed trait DragPayload extends Product with Serializable
sealed trait DragTarget extends Product with Serializable
sealed trait DragPayloadAndTarget extends DragPayload with DragTarget
object DragItem {
  case object DisableDrag extends DragPayloadAndTarget

  sealed trait AnyNodes extends DragPayloadAndTarget { def nodeIds: Iterable[NodeId] }
  sealed trait SingleNode extends AnyNodes { def nodeId: NodeId; @inline def nodeIds: Iterable[NodeId] = nodeId :: Nil }
  sealed trait ChildNode extends SingleNode
  sealed trait ParentNode extends SingleNode
  sealed trait MultiParentNodes extends AnyNodes

  case class Tag(nodeId: NodeId) extends ParentNode { override def toString = s"Tag(${nodeId.shortHumanReadable})"}
  case class Channel(nodeId: NodeId) extends ParentNode { override def toString = s"Channel(${nodeId.shortHumanReadable})"}
  case class User(userId: UserId) extends SingleNode { @inline def nodeId = userId.asInstanceOf[NodeId];  override def toString = s"User(${nodeId.shortHumanReadable})" }
  case class SelectedNode(nodeId: NodeId) extends SingleNode { override def toString = s"SelectedNode(${nodeId.shortHumanReadable})"}
  case class SelectedNodes(nodeIds: Seq[NodeId]) extends DragPayload { override def toString = s"SelectedNodes(${nodeIds.map(_.shortHumanReadable).mkString(",")})"}

  case class Message(nodeId: NodeId) extends ChildNode { override def toString = s"Message(${nodeId.shortHumanReadable})"}
  case class Thread(nodeIds: Seq[NodeId]) extends MultiParentNodes { override def toString = s"Thread(${nodeIds.map(_.shortHumanReadable).mkString(",")})"}
  case class Page(nodeId: NodeId) extends ParentNode { override def toString = s"Page(${nodeIds.map(_.shortHumanReadable).mkString(",")})"}

  case class Stage(nodeId: NodeId) extends ParentNode { override def toString = s"Column(${nodeId.shortHumanReadable})"}
  case class Task(nodeId: NodeId) extends ChildNode { override def toString = s"Task(${nodeId.shortHumanReadable})"}

  case object Sidebar extends DragTarget

  val payloadPropName = "_wust_dragpayload"
  val targetPropName = "_wust_dragtarget"
  val draggedActionPropName = "_wust_draggedaction"
}

sealed trait DragContainer
sealed trait SortableContainer extends DragContainer { def parentId: NodeId; def items: Seq[NodeId] }
object DragContainer {
  case object Default extends DragContainer
  object Kanban {
    sealed trait AreaForColumns extends SortableContainer
    sealed trait AreaForCards extends SortableContainer
    sealed trait Workspace extends SortableContainer { def parentId: NodeId }
    case class Column(nodeId:NodeId, items: Seq[NodeId], workspace:NodeId) extends AreaForColumns with AreaForCards { @inline def parentId = nodeId; override def toString = s"Column(${parentId.shortHumanReadable})" }
    case class ColumnArea(parentId:NodeId, items: Seq[NodeId]) extends AreaForColumns { override def toString = s"ColumnArea(${parentId.shortHumanReadable})"}
    case class Inbox(parentId:NodeId, items: Seq[NodeId]) extends AreaForCards with Workspace { override def toString = s"Inbox(${parentId.shortHumanReadable})"}
    case class Card(parentId:NodeId, items: Seq[NodeId]) extends AreaForCards with Workspace { override def toString = s"Card(${parentId.shortHumanReadable})"}
  }

  case class List(parentId: NodeId, items: Seq[NodeId]) extends SortableContainer
  // Fixme: items workaround. Differentiate what is parent and what are the items
  // case class AvatarHolder(nodeId: NodeId) extends DragContainer { @inline def parentId = nodeId; @inline def items = Seq(nodeId); }

  case object Sidebar extends DragContainer
  case object Chat extends DragContainer

  val propName = "_wust_dragcontainer"
}

