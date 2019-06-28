package wust.graph

import wust.ids._

sealed trait Edge {
  def sourceId: NodeId
  def targetId: NodeId
  def data: EdgeData
  // a copy method to change the sourceId and/or targetId of an edge
  // without pattern matching over all edge types.
  def copyId(sourceId: NodeId, targetId: NodeId): Edge

  @inline def as[T <: Edge]: T = asInstanceOf[T]
}

/**
  * Here, all edge types are implemented.
  * CONVENTION: The arguments orders is based the traverse order of the edge and therefore the permission propagation.
  * Hence, an edge with a (NodeId, UserId) pair starts with the NodeId, followed by the EdgeData and the UserId.
  * The UserId acts as a traverse stop.
  * In pairs of (NodeId, NodeId), the first NodeId must be the one that is traversed first, e.g. the LabeledProperty Edge
  * holds the NodeId of the content node and the second parameter holds the property value.
  */
object Edge {

  sealed trait User  extends Edge {
    def userId: UserId
    def nodeId: NodeId
    def sourceId: NodeId = nodeId
    def targetId: NodeId = userId
  }

  sealed trait Content extends Edge

  // User-Edges
  final case class Assigned(nodeId: NodeId, userId: UserId) extends Edge.User {
    def data = EdgeData.Assigned
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }

  final case class Author(nodeId: NodeId, data: EdgeData.Author, userId: UserId) extends Edge.User {
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }

  final case class Expanded(nodeId: NodeId, data: EdgeData.Expanded, userId: UserId) extends Edge.User {
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }

  final case class Invite(nodeId: NodeId, userId: UserId) extends Edge.User {
    def data = EdgeData.Invite
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }

  final case class Member(nodeId: NodeId, data: EdgeData.Member, userId: UserId) extends Edge.User {
    def level = data.level
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }
  final case class Notify(nodeId: NodeId, userId: UserId) extends Edge.User {
    def data = EdgeData.Notify
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }

  final case class Pinned(nodeId: NodeId, userId: UserId) extends Edge.User {
    def data = EdgeData.Pinned
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }

  final case class Read(nodeId: NodeId, data: EdgeData.Read, userId: UserId) extends Edge.User {
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }

  // Content-Edges
  final case class Automated(nodeId: NodeId, templateNodeId: TemplateId) extends Edge.Content {
    def sourceId = nodeId
    def targetId = templateNodeId
    def data = EdgeData.Automated
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, templateNodeId = TemplateId(targetId))
  }

  final case class Child(parentId: ParentId, data: EdgeData.Child, childId: ChildId) extends Edge.Content {
    def sourceId: NodeId = parentId
    def targetId: NodeId = childId
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(parentId = ParentId(sourceId), childId = ChildId(targetId))
  }
  object Child extends ((ParentId, ChildId) => Child) {
    @inline def delete(parentId: ParentId, childId: ChildId): Child = delete(parentId, EpochMilli.now, childId)
    @inline def delete(parentId: ParentId, deletedAt: EpochMilli, childId: ChildId): Child = Child(parentId, EdgeData.Child(deletedAt, ordering = CuidOrdering.calculate(childId)), childId)
    @inline def apply(parentId: ParentId, childId: ChildId): Child = Child(parentId, EdgeData.Child(ordering = CuidOrdering.calculate(childId)), childId)
    @inline def apply(parentId: ParentId, deletedAt: Option[EpochMilli], childId: ChildId): Child = Child(parentId, EdgeData.Child(deletedAt, ordering = CuidOrdering.calculate(childId)), childId)
  }

  final case class DerivedFromTemplate(nodeId: NodeId, data: EdgeData.DerivedFromTemplate, referenceNodeId: TemplateId) extends Edge.Content {
    def sourceId = nodeId
    def targetId = referenceNodeId
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, referenceNodeId = TemplateId(targetId))
  }

  final case class LabeledProperty(nodeId: NodeId, data: EdgeData.LabeledProperty, propertyId: PropertyId) extends Edge.Content {
    def sourceId = nodeId
    def targetId = propertyId
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, propertyId = PropertyId(targetId))
  }

  final case class Mention(nodeId: NodeId, data: EdgeData.Mention, mentionedId: NodeId) extends Edge.Content {
    def sourceId = nodeId
    def targetId = mentionedId
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, mentionedId = targetId)
  }

  def apply(sourceId:NodeId, data:EdgeData, targetId:NodeId): Edge = data match {
    case EdgeData.Assigned                  => new Edge.Assigned(sourceId, UserId(targetId))
    case EdgeData.Invite                    => new Edge.Invite(sourceId, UserId(targetId))
    case EdgeData.Notify                    => new Edge.Notify(sourceId, UserId(targetId))
    case EdgeData.Pinned                    => new Edge.Pinned(sourceId, UserId(targetId))
    case data: EdgeData.Expanded            => new Edge.Expanded(sourceId, data, UserId(targetId))
    case data: EdgeData.Author              => new Edge.Author(sourceId, data, UserId(targetId))
    case data: EdgeData.Member              => new Edge.Member(sourceId, data, UserId(targetId))
    case data: EdgeData.Read                => new Edge.Read(sourceId, data, UserId(targetId))

    case EdgeData.Automated                 => new Edge.Automated(sourceId, TemplateId(targetId))
    case data: EdgeData.Child               => new Edge.Child(ParentId(sourceId), data, ChildId(targetId))
    case data: EdgeData.LabeledProperty     => new Edge.LabeledProperty(sourceId, data, PropertyId(targetId))
    case data: EdgeData.DerivedFromTemplate => new Edge.DerivedFromTemplate(sourceId, data, TemplateId(targetId))
    case data: EdgeData.Mention             => new Edge.Mention(sourceId, data, MentionedId(targetId))
  }
}

sealed trait EdgeEquality { def eq(other: EdgeEquality): Boolean }
object EdgeEquality {
  case object Never extends EdgeEquality {
    def eq(other: EdgeEquality): Boolean = false
  }
  final case class Unique(sourceId: NodeId, tpe: EdgeData.Type, key: String, targetId: NodeId) extends EdgeEquality {
    def eq(other: EdgeEquality): Boolean = equals(other)
  }
  object Unique {
    // unique constraints how they are defined in the database for edges
    def apply(edge: Edge): Option[Unique] = edge match {
      case _: Edge.Author => None
      case e: Edge.LabeledProperty => Some(Unique(e.sourceId, e.data.tpe, e.data.key, e.targetId))
      case e => Some(Unique(e.sourceId, e.data.tpe, null, e.targetId))
    }
  }

  def apply(edge: Edge): EdgeEquality = Unique(edge).getOrElse(Never)
}
