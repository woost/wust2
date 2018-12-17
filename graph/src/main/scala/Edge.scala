package wust.graph

import wust.ids._
import wust.util.Memo
import wust.util.algorithm._
import wust.util.collection._

import collection.mutable
import collection.breakOut

sealed trait Edge {
  def sourceId: NodeId
  def targetId: NodeId
  def data: EdgeData
  def copyId(sourceId: NodeId, targetId: NodeId): Edge
}

object Edge {

  case class Member(userId: UserId, data: EdgeData.Member, nodeId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = nodeId
    def level = data.level
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(userId = UserId(sourceId), nodeId = targetId)
  }
  case class Author(userId: UserId, data: EdgeData.Author, nodeId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = nodeId
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(userId = UserId(sourceId), nodeId = targetId)
  }

  case class Parent(childId: NodeId, data: EdgeData.Parent, parentId: NodeId) extends Edge {
    def sourceId = childId
    def targetId = parentId
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(childId = sourceId, parentId = targetId)
  }
  object Parent extends ((NodeId, NodeId) => Parent) {
    def delete(childId: NodeId, parentId: NodeId, deletedAt: EpochMilli = EpochMilli.now): Parent = Parent(childId, EdgeData.Parent(Some(deletedAt), None), parentId)
    def apply(childId: NodeId, parentId: NodeId): Parent = Parent(childId, EdgeData.Parent, parentId)
  }

  case class Expanded(userId: UserId, nodeId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = nodeId
    def data = EdgeData.Expanded
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(userId = UserId(sourceId), nodeId = targetId)
  }

  case class Assigned(userId: UserId, nodeId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = nodeId
    def data = EdgeData.Assigned
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(userId = UserId(sourceId), nodeId = targetId)
  }

  case class Notify(nodeId: NodeId, userId: UserId) extends Edge {
    def sourceId = nodeId
    def targetId = userId
    def data = EdgeData.Notify
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(userId = UserId(sourceId), nodeId = targetId)
  }

  case class Label(sourceId: NodeId, data: EdgeData.Label, targetId: NodeId) extends Edge {
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(sourceId = sourceId, targetId = targetId)
  }

  case class Pinned(userId: UserId, nodeId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = nodeId
    def data = EdgeData.Pinned
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(userId = UserId(sourceId), nodeId = targetId)
  }
  case class Invite(userId: UserId, nodeId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = nodeId
    def data = EdgeData.Invite
    def copyId(sourceId: NodeId, targetId: NodeId) = copy(userId = UserId(sourceId), nodeId = targetId)
  }

  def apply(sourceId:NodeId, data:EdgeData, targetId:NodeId):Edge = data match {
    case data: EdgeData.Author        => new Edge.Author(UserId(sourceId), data, targetId)
    case data: EdgeData.Member        => new Edge.Member(UserId(sourceId), data, targetId)
    case data: EdgeData.Parent        => new Edge.Parent(sourceId, data, targetId)
    case data: EdgeData.Label         => new Edge.Label(sourceId, data, targetId)
    case EdgeData.Notify              => new Edge.Notify(sourceId, UserId(targetId))
    case EdgeData.Expanded            => new Edge.Expanded(UserId(sourceId), targetId)
    case EdgeData.Assigned            => new Edge.Assigned(UserId(sourceId), targetId)
    case EdgeData.Pinned              => new Edge.Pinned(UserId(sourceId), targetId)
    case EdgeData.Invite              => new Edge.Invite(UserId(sourceId), targetId)
  }
}
