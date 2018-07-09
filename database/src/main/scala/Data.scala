package wust.db

import java.time.{Instant, LocalDateTime, ZoneOffset}

import wust.ids._

import scala.collection.mutable

object Data {
  val DEFAULT = 0L

  case class Node(
      id: NodeId,
      data: NodeData,
      accessLevel: NodeAccess
  )

  case class User(
      id: UserId,
      data: NodeData.User,
      accessLevel: NodeAccess
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

  // adjacency list which comes out of postgres stored procedure graph_page(parents, children, userid)
  case class GraphRow(
      nodeId: NodeId,
      data: NodeData,
      accessLevel: NodeAccess,
      targetIds: List[NodeId],
      edgeData: List[EdgeData]
  ) {
    require(targetIds.size == edgeData.size, "targetIds and connectionData need to have same arity")
  }
  case class Graph(nodes: Seq[Node], edges: Seq[Edge])
  object Graph {
    def from(rowsList: List[GraphRow]): Graph = {
      val rows = rowsList.toArray
      val posts = mutable.ArrayBuffer.empty[Node]
      val connections = mutable.ArrayBuffer.empty[Edge]
      var i = 0
      var j = 0
      while (i < rows.length) {
        val row = rows(i)
        val targetIds = row.targetIds
        val post = Node(row.nodeId, row.data, row.accessLevel)

        posts += post

        j = 0
        while (j < row.targetIds.length) {
          val connectionData = row.edgeData(j)
          val targetId = targetIds(j)

          connections += Edge(post.id, connectionData, targetId)
          j += 1
        }

        i += 1
      }
      Graph(posts, connections)
    }
  }
}
