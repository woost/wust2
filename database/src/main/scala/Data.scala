package wust.db

import java.time.{Instant, LocalDateTime, ZoneOffset}

import wust.ids._

import scala.collection.mutable

object Data {
  val DEFAULT = 0L

  case class Node(
    id: NodeId,
    data: NodeData,
    role: NodeRole,
    accessLevel: NodeAccess
  )

  case class User(
      id: UserId,
      data: NodeData.User,
      accessLevel: NodeAccess
  )

  case class UserDetail(
      userId: UserId,
      email: Option[String],
      verified: Boolean
  )

  case class SimpleUser(id: UserId, data: NodeData.User)

  case class Edge(sourceId: NodeId, data: EdgeData, targetId: NodeId)

  case class MemberEdge(sourceId: UserId, data: EdgeData.Member, targetId: NodeId)

  case class Password(userId: UserId, digest: Array[Byte])
  case class WebPushSubscription(
      id: Long,
      userId: UserId,
      endpointUrl: String,
      p256dh: String,
      auth: String
  ) //TODO: better names. What is pd256dh?

  object WebPushSubscription {
    def apply(
        userId: UserId,
        endpointUrl: String,
        p256dh: String,
        auth: String
    ): WebPushSubscription = {
      WebPushSubscription(DEFAULT, userId, endpointUrl, p256dh, auth)
    }
  }

  // Result of notifiedUsersByNodes
  case class RawPushData(subscription: Data.WebPushSubscription, notifiedNodes: List[NodeId], subscribedNode: NodeId, subscribedNodeContent: String)
  case class NotifyRow(userId: UserId, nodeIds: List[NodeId], subscribedNode: NodeId)
  case class WebPushNotifications(
    id: Long,
    userId: UserId,
    endpointUrl: String,
    p256dh: String,
    auth: String,
    notifiedNodes: List[NodeId],
    subscribedNodeId: NodeId,
    subscribedNodeContent: String
  )

  // adjacency list which comes out of postgres stored procedure graph_page(parents, children, userid)
  case class GraphRow(
    nodeId: NodeId,
    data: NodeData,
    role: NodeRole,
    accessLevel: NodeAccess,
    targetIds: List[NodeId],
    edgeData: List[EdgeData]
  ) {
    require(targetIds.length == edgeData.length, "targetIds and connectionData need to have same arity")
  }
  case class Graph(nodes: Array[Node], edges: Array[Edge])
  object Graph {
    def from(rows: Seq[GraphRow]): Graph = {
      val nodes = mutable.ArrayBuilder.make[Node]
      val edges = mutable.ArrayBuilder.make[Edge]
      nodes.sizeHint(rows.length)

      rows.foreach { row =>
        nodes += Node(row.nodeId, row.data, row.role, row.accessLevel)

        (row.targetIds zip row.edgeData).foreach { case (targetId, edgeData) =>
          edges += Edge(row.nodeId, edgeData, targetId)
        }
      }
      Graph(nodes.result(), edges.result())
    }
  }
}
