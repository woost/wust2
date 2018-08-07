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
  //TODO: should Edge have a equals and hashcode depending only on sourceid, targetid and data.str?
  sealed trait Content extends Edge

  //TODO should have constructor: level: AccessLevel // or not: this makes it less extensible if you add fields to EdgeData
  case class Member(userId: UserId, data: EdgeData.Member, channelId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = channelId
  }
  //TODO should have constructor: timestamp: Timestamp // or not: this makes it less extensible if you add fields to EdgeData
  case class Author(userId: UserId, data: EdgeData.Author, nodeId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = nodeId
  }

  case class Parent(childId: NodeId, parentId: NodeId) extends Content {
    def sourceId = childId
    def targetId = parentId
    def data = EdgeData.Parent
  }

  case class DeletedParent(childId: NodeId, data: EdgeData.DeletedParent, parentId: NodeId)
      extends Content {
    def sourceId = childId
    def targetId = parentId
  }

  case class StaticParentIn(childId: NodeId, parentId: NodeId) extends Content {
    def sourceId = childId
    def targetId = parentId
    def data = EdgeData.StaticParentIn
  }

  case class Notify(nodeId: NodeId, userId: UserId)
      extends Content {
    def sourceId = nodeId
    def targetId = userId
    def data = EdgeData.Notify
  }

  //TODO should have constructor: label: String
  case class Label(sourceId: NodeId, data: EdgeData.Label, targetId: NodeId) extends Content


  def apply(sourceId:NodeId, data:EdgeData, targetId:NodeId):Edge = data match {
    case data: EdgeData.Author        => new Edge.Author(UserId(sourceId), data, targetId)
    case data: EdgeData.Member        => new Edge.Member(UserId(sourceId), data, targetId)
    case EdgeData.Parent              => new Edge.Parent(sourceId, targetId)
    case EdgeData.StaticParentIn      => new Edge.StaticParentIn(sourceId, targetId)
    case data: EdgeData.DeletedParent => new Edge.DeletedParent(sourceId, data, targetId)
    case data: EdgeData.Label         => new Edge.Label(sourceId, data, targetId)
    case EdgeData.Notify              => new Edge.Notify(sourceId, UserId(targetId))
  }
}
