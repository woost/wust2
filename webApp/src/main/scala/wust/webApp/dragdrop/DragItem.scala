package wust.webApp.dragdrop

import outwatch.reactive._
import wust.graph.Edge
import wust.ids.{NodeId, NodeRole, UserId}
import scala.scalajs.js

sealed trait DragPayload extends Product with Serializable { def nodeIds: Seq[NodeId] }
sealed trait DragTarget extends Product with Serializable { def nodeIds: Seq[NodeId] }
sealed trait DragPayloadAndTarget extends DragPayload with DragTarget
object DragItem {
  case object DisableDrag extends DragPayloadAndTarget { def nodeIds = Seq.empty }

  sealed trait ContentNode extends DragPayloadAndTarget { def nodeId: NodeId; def nodeIds = Seq(nodeId) }
  final case class Message(nodeId: NodeId) extends ContentNode { override def toString = s"Message(${nodeId.shortHumanReadable})" }
  final case class Task(nodeId: NodeId) extends ContentNode { override def toString = s"Task(${nodeId.shortHumanReadable})" }
  final case class Note(nodeId: NodeId) extends ContentNode { override def toString = s"Note(${nodeId.shortHumanReadable})" }
  final case class Project(nodeId: NodeId) extends ContentNode { override def toString = s"Project(${nodeId.shortHumanReadable})" }

  sealed trait ContentNodeConnect extends ContentNode {
    def propertyName: String
  }
  final case class TaskConnect(nodeId: NodeId, propertyName: String) extends ContentNodeConnect { override def toString = s"TaskConnect(${nodeId.shortHumanReadable}, $propertyName)" }
  final case class Tag(nodeId: NodeId) extends DragPayloadAndTarget { def nodeIds = Seq(nodeId); override def toString = s"Tag(${nodeId.shortHumanReadable})" }
  final case class Property(edge: Edge.LabeledProperty) extends DragPayloadAndTarget { def nodeIds = Seq.empty; override def toString = s"Property($edge)" }

  final case class Thread(nodeIds: Seq[NodeId]) extends DragTarget { override def toString = s"Thread(${nodeIds.map(_.shortHumanReadable).mkString(",")})" }
  final case class Stage(nodeId: NodeId) extends DragPayloadAndTarget { def nodeIds = Seq(nodeId); override def toString = s"Stage(${nodeId.shortHumanReadable})" }
  final case class CalendarDay(date:js.Date) extends DragTarget { def nodeIds = Seq.empty }

  case object Sidebar extends DragTarget { def nodeIds = Seq.empty }
  final case class Channel(nodeId: NodeId, parentId: Option[NodeId]) extends DragPayloadAndTarget { def nodeIds = Seq(nodeId); override def toString = s"Channel(${nodeId.shortHumanReadable}, parentId: ${parentId.map(_.shortHumanReadable)})" }
  final case class BreadCrumb(nodeId: NodeId) extends DragPayloadAndTarget { def nodeIds = Seq(nodeId); override def toString = s"BreadCrumb(${nodeId.shortHumanReadable})" }
  final case class Workspace(nodeId: NodeId) extends DragTarget { def nodeIds = Seq(nodeId); override def toString = s"Workspace(${nodeId.shortHumanReadable})" }
  final case class TagBar(nodeId: NodeId) extends DragTarget { def nodeIds = Seq(nodeId); override def toString = s"TagBar(${nodeId.shortHumanReadable})" }

  final case class User(userId: UserId) extends DragPayload { @inline def nodeId = userId: NodeId; def nodeIds = Seq(nodeId); override def toString = s"User(${nodeId.shortHumanReadable})" }

  final case class SelectedNode(nodeId: NodeId) extends DragPayload { def nodeIds = Seq(nodeId); override def toString = s"SelectedNode(${nodeId.shortHumanReadable})" }
  final case class SelectedNodes(nodeIds: Seq[NodeId]) extends DragPayload { override def toString = s"SelectedNodes(${nodeIds.map(_.shortHumanReadable).mkString(",")})" }

  def fromNodeRole(nodeId: NodeId, role: NodeRole): Option[DragPayloadAndTarget] = Some(role) collect {
    case NodeRole.Message => DragItem.Message(nodeId)
    case NodeRole.Task    => DragItem.Task(nodeId)
    case NodeRole.Note    => DragItem.Note(nodeId)
    case NodeRole.Project => DragItem.Project(nodeId)
    case NodeRole.Tag     => DragItem.Tag(nodeId)
    case NodeRole.Stage   => DragItem.Stage(nodeId)
  }

  val payloadPropName = "_wust_dragpayload"
  val targetPropName = "_wust_dragtarget"
  val draggedActionPropName = "_wust_draggedaction"
}

sealed trait DragContainer extends Product with Serializable {
  val triggerRepair = SinkSourceHandler.publish[Unit]
}
sealed trait SortableContainer extends DragContainer {
  def parentId: NodeId
  def items: Seq[NodeId]
}
object DragContainer {
  case object Default extends DragContainer
  object Kanban {
    sealed trait AreaForColumns extends SortableContainer
    sealed trait AreaForCards extends SortableContainer
    sealed trait Workspace extends SortableContainer { def parentId: NodeId }
    final case class Column(nodeId: NodeId, items: Seq[NodeId], workspace: NodeId) extends AreaForColumns with AreaForCards { @inline def parentId = nodeId; override def toString = s"Column(${parentId.shortHumanReadable})" }
    final case class ColumnArea(parentId: NodeId, items: Seq[NodeId]) extends AreaForColumns { override def toString = s"ColumnArea(${parentId.shortHumanReadable})" }
    final case class Inbox(parentId: NodeId, items: Seq[NodeId]) extends AreaForCards with Workspace { override def toString = s"Inbox(${parentId.shortHumanReadable})" }
    final case class Card(parentId: NodeId, items: Seq[NodeId]) extends AreaForCards with Workspace { override def toString = s"Card(${parentId.shortHumanReadable})" }
  }

  // Fixme: items workaround. Differentiate what is parent and what are the items
  // final case class AvatarHolder(nodeId: NodeId) extends DragContainer { @inline def parentId = nodeId; @inline def items = Seq(nodeId); }

  case object Sidebar extends DragContainer
  case object Chat extends DragContainer
  case object Calendar extends DragContainer

  val propName = "_wust_dragcontainer"
}
