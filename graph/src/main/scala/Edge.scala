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
}

object Edge {
  sealed trait Content extends Edge

  case class Member(userId: UserId, data: EdgeData.Member, channelId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = channelId
  }
  case class Author(userId: UserId, data: EdgeData.Author, nodeId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = nodeId
  }

  case class Parent(childId: NodeId, data: EdgeData.Parent, parentId: NodeId) extends Content {
    def sourceId = childId
    def targetId = parentId
  }
  object Parent extends ((NodeId, NodeId) => Parent) {
    def delete(childId: NodeId, parentId: NodeId): Parent = Parent(childId, EdgeData.Parent(EpochMilli.now), parentId)
    def apply(childId: NodeId, parentId: NodeId): Parent = Parent(childId, EdgeData.Parent, parentId)
  }

  case class StaticParentIn(childId: NodeId, parentId: NodeId) extends Content {
    def sourceId = childId
    def targetId = parentId
    def data = EdgeData.StaticParentIn
  }

  case class Expanded(userId: UserId, nodeId: NodeId) extends Content {
    def sourceId = userId
    def targetId = nodeId
    def data = EdgeData.Expanded
  }

  case class Notify(nodeId: NodeId, userId: UserId)
      extends Content {
    def sourceId = nodeId
    def targetId = userId
    def data = EdgeData.Notify
  }

  case class Label(sourceId: NodeId, data: EdgeData.Label, targetId: NodeId) extends Content

  def apply(sourceId:NodeId, data:EdgeData, targetId:NodeId):Edge = data match {
    case data: EdgeData.Author        => new Edge.Author(UserId(sourceId), data, targetId)
    case data: EdgeData.Member        => new Edge.Member(UserId(sourceId), data, targetId)
    case data: EdgeData.Parent        => new Edge.Parent(sourceId, data, targetId)
    case EdgeData.StaticParentIn      => new Edge.StaticParentIn(sourceId, targetId)
    case data: EdgeData.Label         => new Edge.Label(sourceId, data, targetId)
    case EdgeData.Notify              => new Edge.Notify(sourceId, UserId(targetId))
    case EdgeData.Expanded             => new Edge.Expanded(UserId(sourceId), targetId)
  }
}
