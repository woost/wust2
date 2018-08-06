package wust.sdk

import wust.graph.{Edge, GraphChanges, Node, NodeMeta}
import wust.ids._

object EventMapper {

  def createNodeInWust(nodeContent: Node.Content, wustAuthorUserId: UserId, timestamp: EpochMilli, parents: Set[NodeId], additionalMembers: Set[UserId]): GraphChanges = {

//    val nodeAuthorEdge = Edge.Author(wustAuthorUserId, EdgeData.Author(timestamp), nodeContent.id)
//    val nodeAuthorMemberEdge = Edge.Member(wustAuthorUserId, EdgeData.Member(AccessLevel.ReadWrite), nodeContent.id)

    val parentEdges: Set[Edge] = parents.map(parent => Edge.Parent(nodeContent.id, parent))
//    val memberEdges: Set[Edge] = additionalMembers.map(member => Edge.Member(member, EdgeData.Member(AccessLevel.ReadWrite), nodeContent.id))


    GraphChanges(
      addNodes = Set(
        nodeContent
      ),
      addEdges = parentEdges
//        ++ memberEdges
//        ++ Set(
//        nodeAuthorEdge, nodeAuthorMemberEdge
//      )
    )
  }
  def createMessageInWust(nodeData: NodeData.Content, wustAuthorUserId: UserId, timestamp: EpochMilli, channel: NodeId, additionalParents: Set[NodeId] = Set.empty, additionalMembers: Set[UserId] = Set.empty): (NodeId, GraphChanges) = {
    val node = Node.Content(nodeData)
    val message = createNodeInWust(node, wustAuthorUserId, timestamp, additionalParents + channel, additionalMembers)
    (node.id, message)
  }

  def editMessageContentInWust(contentNode: Node.Content, newContent: NodeData.Content): (NodeId, GraphChanges) = {
    val editedMessage = GraphChanges(
      addNodes = Set(
        contentNode.copy(data = newContent)
      )
    )
    (contentNode.id, editedMessage)
  }

  def deleteMessageInWust(nodeId: NodeId, channelId: NodeId): GraphChanges = {
    GraphChanges.disconnect(Edge.Parent)(
      nodeId,
      channelId
    )
  }

}
