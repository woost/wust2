package wust.graph

import wust.ids._

sealed trait Edge {
  def sourceId: NodeId
  def targetId: NodeId
  def data: EdgeData
  // a copy method to change the sourceId and/or targetId of an edge
  // without pattern matching over all edge types.
  def copyId(sourceId: NodeId, targetId: NodeId): Edge
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
  case class Assigned(nodeId: NodeId, userId: UserId) extends Edge.User {
    def data = EdgeData.Assigned
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }

  case class Author(nodeId: NodeId, data: EdgeData.Author, userId: UserId) extends Edge.User {
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }

  case class Expanded(nodeId: NodeId, userId: UserId) extends Edge.User {
    def data = EdgeData.Expanded
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }

  case class Invite(nodeId: NodeId, userId: UserId) extends Edge.User {
    def data = EdgeData.Invite
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }

  case class Member(nodeId: NodeId, data: EdgeData.Member, userId: UserId) extends Edge.User {
    def level = data.level
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }
  case class Notify(nodeId: NodeId, userId: UserId) extends Edge.User {
    def data = EdgeData.Notify
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }

  case class Pinned(nodeId: NodeId, userId: UserId) extends Edge.User {
    def data = EdgeData.Pinned
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, userId = UserId(targetId))
  }

  // Content-Edges
  case class Automated(nodeId: NodeId, templateNodeId: TemplateId) extends Edge.Content {
    def sourceId = nodeId
    def targetId = templateNodeId
    def data = EdgeData.Automated
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, templateNodeId = TemplateId(targetId))
  }

  case class Child(parentId: ParentId, data: EdgeData.Child, childId: ChildId) extends Edge.Content {
    def sourceId: NodeId = parentId
    def targetId: NodeId = childId
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(parentId = ParentId(sourceId), childId = ChildId(targetId))
  }
  object Child extends ((ParentId, ChildId) => Child) {
    def delete(parentId: ParentId, childId: ChildId, deletedAt: EpochMilli = EpochMilli.now): Child = Child(parentId, EdgeData.Child(Some(deletedAt), None), childId)
    def apply(parentId: ParentId, childId: ChildId): Child = Child(parentId, EdgeData.Child, childId)
  }

  case class DerivedFromTemplate(nodeId: NodeId, data: EdgeData.DerivedFromTemplate, referenceNodeId: TemplateId) extends Edge.Content {
    def sourceId = nodeId
    def targetId = referenceNodeId
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, referenceNodeId = TemplateId(targetId))
  }

  case class LabeledProperty(nodeId: NodeId, data: EdgeData.LabeledProperty, propertyId: PropertyId) extends Edge.Content {
    def sourceId = nodeId
    def targetId = propertyId
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(nodeId = sourceId, propertyId = PropertyId(targetId))
  }

  def apply(sourceId:NodeId, data:EdgeData, targetId:NodeId): Edge = data match {
    case EdgeData.Assigned                  => new Edge.Assigned(sourceId, UserId(targetId))
    case EdgeData.Expanded                  => new Edge.Expanded(sourceId, UserId(targetId))
    case EdgeData.Invite                    => new Edge.Invite(sourceId, UserId(targetId))
    case EdgeData.Notify                    => new Edge.Notify(sourceId, UserId(targetId))
    case EdgeData.Pinned                    => new Edge.Pinned(sourceId, UserId(targetId))
    case data: EdgeData.Author              => new Edge.Author(sourceId, data, UserId(targetId))
    case data: EdgeData.Member              => new Edge.Member(sourceId, data, UserId(targetId))

    case EdgeData.Automated                 => new Edge.Automated(sourceId, TemplateId(targetId))
    case data: EdgeData.Child               => new Edge.Child(ParentId(sourceId), data, ChildId(targetId))
    case data: EdgeData.LabeledProperty     => new Edge.LabeledProperty(sourceId, data, PropertyId(targetId))
    case data: EdgeData.DerivedFromTemplate => new Edge.DerivedFromTemplate(sourceId, data, TemplateId(targetId))
  }
}
