package wust.webApp.dragdrop

import wust.ids.{ NodeRole, NodeId, UserId }
import wust.graph.Edge

sealed trait DragPayload extends Product with Serializable
sealed trait DragTarget extends Product with Serializable
sealed trait DragPayloadAndTarget extends DragPayload with DragTarget
object DragItem {
  case object DisableDrag extends DragPayloadAndTarget

  sealed trait ContentNode extends DragPayloadAndTarget { def nodeId: NodeId; def parentId: Option[NodeId] }
  case class Message(nodeId: NodeId, parentId: Option[NodeId]) extends ContentNode { override def toString = s"Message(${nodeId.shortHumanReadable}, parent = ${parentId.map(_.shortHumanReadable)})" }
  case class Task(nodeId: NodeId, parentId: Option[NodeId]) extends ContentNode { override def toString = s"Task(${nodeId.shortHumanReadable}, parent = ${parentId.map(_.shortHumanReadable)})" }
  case class Note(nodeId: NodeId, parentId: Option[NodeId]) extends ContentNode { override def toString = s"Note(${nodeId.shortHumanReadable}, parent = ${parentId.map(_.shortHumanReadable)})" }
  case class Project(nodeId: NodeId, parentId: Option[NodeId]) extends ContentNode { override def toString = s"Project(${nodeId.shortHumanReadable}, parent = ${parentId.map(_.shortHumanReadable)})" }

  sealed trait ContentNodeConnect extends ContentNode {
    def propertyName: String
  }
  case class TaskConnect(nodeId: NodeId, parentId: Option[NodeId], propertyName: String) extends ContentNodeConnect { override def toString = s"TaskConnect(${nodeId.shortHumanReadable}, parent = ${parentId.map(_.shortHumanReadable)}, $propertyName)" }
  case class Tag(nodeId: NodeId, parentId: Option[NodeId]) extends DragPayloadAndTarget { override def toString = s"Tag(${nodeId.shortHumanReadable})" }
  case class Property(edge: Edge.LabeledProperty) extends DragPayloadAndTarget { override def toString = s"Property($edge)" }

  case class Thread(nodeIds: Seq[NodeId]) extends DragTarget { override def toString = s"Thread(${nodeIds.map(_.shortHumanReadable).mkString(",")})" }
  case class Stage(nodeId: NodeId, parentId: Option[NodeId]) extends DragPayloadAndTarget { override def toString = s"Stage(${nodeId.shortHumanReadable})" }

  case object Sidebar extends DragTarget
  case class Channel(nodeId: NodeId, parentId: Option[NodeId]) extends DragPayloadAndTarget { override def toString = s"Channel(${nodeId.shortHumanReadable}, parentId: ${parentId.map(_.shortHumanReadable)})" }
  case class BreadCrumb(nodeId: NodeId) extends DragPayloadAndTarget { override def toString = s"BreadCrumb(${nodeId.shortHumanReadable})" }
  case class Workspace(nodeId: NodeId) extends DragTarget { override def toString = s"Workspace(${nodeId.shortHumanReadable})" }
  case class TagBar(nodeId: NodeId) extends DragTarget { override def toString = s"TagBar(${nodeId.shortHumanReadable})" }

  case class User(userId: UserId) extends DragPayload { @inline def nodeId = userId: NodeId; override def toString = s"User(${nodeId.shortHumanReadable})" }

  case class SelectedNode(nodeId: NodeId) extends DragPayload { override def toString = s"SelectedNode(${nodeId.shortHumanReadable})" }
  case class SelectedNodes(nodeIds: Seq[NodeId]) extends DragPayload { override def toString = s"SelectedNodes(${nodeIds.map(_.shortHumanReadable).mkString(",")})" }

  def fromNodeRole(nodeId: NodeId, parentId: Option[NodeId], role: NodeRole): Option[DragPayloadAndTarget] = Some(role) collect {
    case NodeRole.Message => DragItem.Message(nodeId, parentId)
    case NodeRole.Task    => DragItem.Task(nodeId, parentId)
    case NodeRole.Note    => DragItem.Note(nodeId, parentId)
    case NodeRole.Project => DragItem.Project(nodeId, parentId)
    case NodeRole.Tag     => DragItem.Tag(nodeId, parentId)
    case NodeRole.Stage   => DragItem.Stage(nodeId, parentId)
  }

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

  // Fixme: items workaround. Differentiate what is parent and what are the items
  // case class AvatarHolder(nodeId: NodeId) extends DragContainer { @inline def parentId = nodeId; @inline def items = Seq(nodeId); }

  case object Sidebar extends DragContainer
  case object Chat extends DragContainer

  val propName = "_wust_dragcontainer"
}
