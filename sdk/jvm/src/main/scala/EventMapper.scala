package wust.sdk

import wust.graph.{Edge, GraphChanges, Node, NodeMeta}
import wust.ids._

object EventMapper {

  def createNodeInWust(nodeContent: Node.Content, wustAuthorUserId: UserId, timestamp: EpochMilli, parents: Set[NodeId], additionalMembers: Set[UserId]): GraphChanges = {

    val nodeAuthorEdge = Edge.Author(wustAuthorUserId, EdgeData.Author(timestamp), nodeContent.id)
    val nodeAuthorMemberEdge = Edge.Member(wustAuthorUserId, EdgeData.Member(AccessLevel.ReadWrite), nodeContent.id)

    val parentEdges: Set[Edge] = parents.map(parent => Edge.Parent(nodeContent.id, parent))
    val memberEdges: Set[Edge] = additionalMembers.map(member => Edge.Member(member, EdgeData.Member(AccessLevel.ReadWrite), nodeContent.id))


    GraphChanges(
      addNodes = Set(
        nodeContent
      ),
      addEdges = Set(
        nodeAuthorEdge, nodeAuthorMemberEdge
      ) ++ memberEdges
        ++ parentEdges
    )
  }
  def createMessageInWust(nodeData: NodeData.Content, wustAuthorUserId: UserId, timestamp: String, channel: NodeId, additionalParents: Set[NodeId] = Set.empty, additionalMembers: Set[UserId] = Set.empty): GraphChanges =
    createNodeInWust(Node.Content(nodeData), wustAuthorUserId, EpochMilli.from(timestamp), additionalParents + channel, additionalMembers)

  def editMessageContentInWust(contentNode: Node.Content, newContent: NodeData.Content): GraphChanges = {
    GraphChanges(
      addNodes = Set(
        contentNode.copy(data = newContent)
      )
    )
  }

  def deleteMessageInWust(nodeId: NodeId, channelId: NodeId): GraphChanges = {
    GraphChanges.disconnectParent(
      nodeId,
      channelId
    )
  }

}
