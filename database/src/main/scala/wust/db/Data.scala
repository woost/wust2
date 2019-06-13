package wust.db

import io.circe.parser._
import wust.ids._
import wust.ids.serialize.Circe._

import scala.collection.mutable

object Data {
  val DEFAULT = 0L

  final case class Node(
    id: NodeId,
    data: NodeData,
    role: NodeRole,
    accessLevel: NodeAccess,
    views: Option[List[View.Visible]]
  )

  //TODO: needed because we cannot parse views properly...
  final case class NodeRaw(
    id: NodeId,
    data: NodeData,
    role: NodeRole,
    accessLevel: NodeAccess,
    views: Option[String]
  ) {
    def toNode: Node = Node(
      id = id,
      data = data,
      role = role,
      accessLevel = accessLevel,
      views = views.map(NodeRaw.viewsFromString)
    )
  }
  object NodeRaw {
    def viewsFromString(str: String): List[View.Visible] = {
      val viewStrings = str
        .drop(2).dropRight(2)
        .split("""","""")
        .map(_.replaceAll("""\\"""", """""""))
        .toList

      viewStrings.flatMap(str => decode[View.Visible](str).right.toOption)
    }
  }

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
  final case class RawPushData(subscription: Data.WebPushSubscription, notifiedNodes: List[NodeId], subscribedNode: NodeId, subscribedNodeContent: String)
  final case class NotifyRow(userId: UserId, nodeIds: List[NodeId], subscribedNode: NodeId)
  final case class WebPushNotifications(
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
  final case class GraphRow(
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
  final case class Graph(nodes: Array[Node], edges: Array[Edge])
  object Graph {
    def from(rows: Seq[GraphRow]): Graph = {
      val nodes = mutable.ArrayBuilder.make[Node]
      val edges = mutable.ArrayBuilder.make[Edge]
      nodes.sizeHint(rows.length)

      rows.foreach { row =>
        //TODO this is really ugly, we want views: Option[List[View]], but quill fails when decoding this graph-row.
        //Now we let quill decode views to Option[String] and decode the list ourselves...meh
        val viewList: Option[List[View.Visible]] = row.views.map(NodeRaw.viewsFromString)
        nodes += Node(row.nodeId, row.data, row.role, row.accessLevel, viewList)

        (row.targetIds zip row.edgeData).foreach { case (targetId, edgeData) =>
          edges += Edge(row.nodeId, edgeData, targetId)
        }
      }
      Graph(nodes.result(), edges.result())
    }
  }
}
