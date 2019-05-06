package wust.db

import java.time.{Instant, LocalDateTime, ZoneOffset}

import wust.ids._

import io.circe.parser._
import io.circe.syntax._
import wust.ids.serialize.Circe._

import scala.collection.mutable

object Data {
  val DEFAULT = 0L

  case class Node(
    id: NodeId,
    data: NodeData,
    role: NodeRole,
    accessLevel: NodeAccess,
    views: Option[List[View.Visible]]
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

  case class MemberEdge(sourceId: NodeId, data: EdgeData.Member, targetId: UserId)

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
  case class NotifyRow(userId: UserId, newCreatedNodes: List[NodeId], subscribedNode: NodeId)
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
    views: Option[String],
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
        //TODO this is really ugly, we want views: Option[List[View]], but quill fails when decoding this graph-row.
        //Now we let quill decode views to Option[String] and decode the list ourselves...meh
        val viewList: Option[List[View.Visible]] = row.views.map { views =>
          val viewStrings = views
            .drop(2).dropRight(2)
            .split("""","""")
            .map(_.replaceAll("""\\"""", """""""))
            .toList
          viewStrings.flatMap(str => decode[View.Visible](str).right.toOption)
        }
        nodes += Node(row.nodeId, row.data, row.role, row.accessLevel, viewList)

        (row.targetIds zip row.edgeData).foreach { case (targetId, edgeData) =>
          edges += Edge(row.nodeId, edgeData, targetId)
        }
      }
      Graph(nodes.result(), edges.result())
    }
  }
}
