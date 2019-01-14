package wust.webApp.dragdrop

import wust.ids.{ NodeId, UserId }

sealed trait DragPayload extends Product with Serializable
sealed trait DragTarget extends Product with Serializable
sealed trait DragPayloadAndTarget extends DragPayload with DragTarget
object DragItem {
  case object DisableDrag extends DragPayloadAndTarget

  sealed trait ContentNode extends DragPayloadAndTarget { def nodeId: NodeId }
  case class Message(nodeId: NodeId) extends ContentNode { override def toString = s"Message(${nodeId.shortHumanReadable})" }
  case class Task(nodeId: NodeId) extends ContentNode { override def toString = s"Task(${nodeId.shortHumanReadable})" }
  case class Project(nodeId: NodeId) extends ContentNode { override def toString = s"Project(${nodeId.shortHumanReadable})" }

  case class Tag(nodeId: NodeId) extends DragPayloadAndTarget { override def toString = s"Tag(${nodeId.shortHumanReadable})" }

  case class Thread(nodeIds: Seq[NodeId]) extends DragTarget { override def toString = s"Thread(${nodeIds.map(_.shortHumanReadable).mkString(",")})" }
  case class Stage(nodeId: NodeId) extends DragPayloadAndTarget { override def toString = s"Column(${nodeId.shortHumanReadable})" }

  case object Sidebar extends DragTarget
  case class Channel(nodeId: NodeId) extends DragPayloadAndTarget { override def toString = s"Channel(${nodeId.shortHumanReadable})" }
  case class BreadCrumb(nodeId: NodeId) extends DragPayloadAndTarget { override def toString = s"BreadCrumb(${nodeId.shortHumanReadable})" }
  case class Workspace(nodeId: NodeId) extends DragTarget { override def toString = s"Workspace(${nodeId.shortHumanReadable})" }
  case class TagBar(nodeId: NodeId) extends DragTarget { override def toString = s"TagBar(${nodeId.shortHumanReadable})" }

  case class User(userId: UserId) extends DragPayload { @inline def nodeId = userId.asInstanceOf[NodeId]; override def toString = s"User(${nodeId.shortHumanReadable})" }

  case class SelectedNode(nodeId: NodeId) extends DragPayload { override def toString = s"SelectedNode(${nodeId.shortHumanReadable})" }
  case class SelectedNodes(nodeIds: Seq[NodeId]) extends DragPayload { override def toString = s"SelectedNodes(${nodeIds.map(_.shortHumanReadable).mkString(",")})" }

  val payloadPropName = "_wust_dragpayload"
  val targetPropName = "_wust_dragtarget"
  val draggedActionPropName = "_wust_draggedaction"
}

sealed trait DragContainer extends Product with Serializable
sealed trait SortableContainer extends DragContainer { def parentId: NodeId; def items: Seq[NodeId] }
object DragContainer {
  case object Default extends DragContainer
  object Kanban {
    sealed trait AreaForColumns extends SortableContainer
    sealed trait AreaForCards extends SortableContainer
    sealed trait Workspace extends SortableContainer { def parentId: NodeId }
    case class Column(nodeId: NodeId, items: Seq[NodeId], workspace: NodeId) extends AreaForColumns with AreaForCards { @inline def parentId = nodeId; override def toString = s"Column(${parentId.shortHumanReadable})" }
    case class ColumnArea(parentId: NodeId, items: Seq[NodeId]) extends AreaForColumns { override def toString = s"ColumnArea(${parentId.shortHumanReadable})" }
    case class Inbox(parentId: NodeId, items: Seq[NodeId]) extends AreaForCards with Workspace { override def toString = s"Inbox(${parentId.shortHumanReadable})" }
    case class Card(parentId: NodeId, items: Seq[NodeId]) extends AreaForCards with Workspace { override def toString = s"Card(${parentId.shortHumanReadable})" }
  }

  case class List(parentId: NodeId, items: Seq[NodeId]) extends SortableContainer
  // Fixme: items workaround. Differentiate what is parent and what are the items
  // case class AvatarHolder(nodeId: NodeId) extends DragContainer { @inline def parentId = nodeId; @inline def items = Seq(nodeId); }

  case object Sidebar extends DragContainer
  case object Chat extends DragContainer

  val propName = "_wust_dragcontainer"
}
