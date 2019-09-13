package wust.db

import io.circe.parser._
import wust.ids._
import wust.ids.serialize.Circe._
import java.util.Date

import scala.collection.mutable

object Data {
  val DEFAULT = 0L

  final case class Node(
    id: NodeId,
    data: NodeData,
    role: NodeRole,
    accessLevel: NodeAccess,
    schema: NodeSchema,
  )

  final case class User(
      id: UserId,
      data: NodeData.User,
      accessLevel: NodeAccess
  )

  final case class UserDetail(
      userId: UserId,
      email: Option[String],
      verified: Boolean
  )

  final case class SimpleUser(id: UserId, data: NodeData.User)

  final case class Edge(sourceId: NodeId, data: EdgeData, targetId: NodeId)

  final case class MemberEdge(sourceId: NodeId, data: EdgeData.Member, targetId: UserId)

  final case class Password(userId: UserId, digest: Array[Byte])
  final case class WebPushSubscription(
      id: Long,
      userId: UserId,
      endpointUrl: String,
      p256dh: String,
      auth: String
  ) { //TODO: better names. What is pd256dh?
    override def toString = s"WebPushSubscription($id, $userId, $endpointUrl, ***, ***)"
  }

  final case class Reminder(
    userId: UserId,
    email: String,
    node: Node
  )

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

  final case class OAuthClient(userId: UserId, service: OAuthClientService, accessToken: String)

  final case class AllowedNodeAccess(nodeId: NodeId, userId: UserId)

  // Result of notifiedUsersByNodes
  final case class NotifiedUsersRow(userId: UserId, notifiedNodes: List[NodeId], subscribedNodeId: NodeId, subscribedNodeContent: String)

  // adjacency list which comes out of postgres stored procedure graph_page(parents, children, userid)
  final case class GraphRow(
    nodeId: Option[NodeId],
    data: Option[NodeData],
    role: Option[NodeRole],
    accessLevel: NodeAccess,
    schema: Option[NodeSchema],
    sourceId: Option[NodeId],
    targetId: Option[NodeId],
    edgeData: Option[EdgeData]
  )

  final case class Graph(nodes: Array[Node], edges: Array[Edge])
  object Graph {
    def from(rows: Seq[GraphRow]): Graph = {
      val nodes = mutable.ArrayBuilder.make[Node]
      val edges = mutable.ArrayBuilder.make[Edge]

      rows.foreach { row =>
        if (row.nodeId.isEmpty) { // edge
          edges += Edge(row.sourceId.get, row.edgeData.get, row.targetId.get)
        } else { // node
          nodes += Node(row.nodeId.get, row.data.get, row.role.get, row.accessLevel, row.schema.get)
        }
      }
      Graph(nodes.result(), edges.result())
    }
  }

  case class UsedFeature(userId: UserId, feature: Feature, timestamp: Date)
}
