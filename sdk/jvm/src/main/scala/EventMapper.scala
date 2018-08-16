package wust.sdk

import wust.graph.{Edge, GraphChanges, Node, NodeMeta}
import wust.ids._

object EventMapper {

  case class CreationResult(nodeId: NodeId, graphChanges: GraphChanges)

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

  def editNodeContentInWust(nodeId: NodeId, nodeContent: NodeData.Content): GraphChanges = {
    GraphChanges(
      addNodes = Set(Node.Content(nodeId, nodeContent))
    )
  }

  def createMessageInWust(nodeData: NodeData.Content, wustAuthorUserId: UserId, timestamp: EpochMilli, channel: NodeId, additionalParents: Set[NodeId] = Set.empty, additionalMembers: Set[UserId] = Set.empty): CreationResult = {
    val node = Node.Content(nodeData)
    val message = createNodeInWust(node, wustAuthorUserId, timestamp, additionalParents + channel, additionalMembers)
    CreationResult(node.id, message)
  }

  def editMessageInWust(nodeId: NodeId, nodeData: NodeData.Content, wustAuthorUserId: UserId, timestamp: EpochMilli, channel: NodeId, additionalParents: Set[NodeId] = Set.empty, additionalMembers: Set[UserId] = Set.empty): GraphChanges = {
    val node = Node.Content(nodeId, nodeData)
    val message = createNodeInWust(node, wustAuthorUserId, timestamp, additionalParents + channel, additionalMembers)
    message
  }

  def editMessageContentInWust(nodeId: NodeId, newContent: NodeData.Content): GraphChanges = {
    editNodeContentInWust(nodeId, newContent)
  }

  def deleteMessageInWust(nodeId: NodeId, channelId: NodeId): GraphChanges = {
    GraphChanges.disconnect(Edge.Parent)(
      nodeId,
      channelId
    )
  }

  def unDeleteMessageInWust(nodeId: NodeId, channelId: NodeId): GraphChanges = {
    GraphChanges.connect(Edge.Parent)(
      nodeId,
      channelId
    )
  }

  def createChannelInWust(nodeData: NodeData.Content, wustAuthorUserId: UserId, timestamp: EpochMilli, channelNodeId: NodeId, additionalParents: Set[NodeId] = Set.empty, additionalMembers: Set[UserId] = Set.empty): CreationResult = {
    val node = Node.Content(nodeData, NodeMeta(NodeAccess.Restricted))
    val message = createNodeInWust(node, wustAuthorUserId, timestamp, additionalParents + channelNodeId, additionalMembers)
    CreationResult(node.id, message)
  }

  def createWorkspaceInWust(nodeData: NodeData.Content, wustAuthorUserId: UserId, timestamp: EpochMilli, workspaceNodeId: NodeId, additionalParents: Set[NodeId] = Set.empty, additionalMembers: Set[UserId] = Set.empty): CreationResult = {
    val node = Node.Content(nodeData, NodeMeta(NodeAccess.Restricted))
    val message = createNodeInWust(node, wustAuthorUserId, timestamp, additionalParents + workspaceNodeId, additionalMembers)
    CreationResult(node.id, message)
  }

  def editChannelInWust(nodeId: NodeId, newName: NodeData.Content): GraphChanges = {
    editNodeContentInWust(nodeId, newName)
  }

  def deleteChannelInWust(channelId: NodeId, workspaceNodeId: NodeId): GraphChanges = {
    GraphChanges.disconnect(Edge.Parent)(
      channelId,
      workspaceNodeId
    )
  }

  def unDeleteChannelInWust(channelId: NodeId, workspaceNodeId: NodeId): GraphChanges = {
    GraphChanges.connect(Edge.Parent)(
      channelId,
      workspaceNodeId
    )
  }




//    def createMessageInSlack(node: Node) = ???

}
