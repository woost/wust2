package wust.sdk

import wust.graph._
import wust.ids._
import scala.collection.breakOut

object EventToGraphChangeMapper {

  case class CreationResult(nodeId: NodeId, graphChanges: GraphChanges)

  def createNodeInWust(nodeContent: Node.Content, wustAuthorUserId: UserId, timestamp: EpochMilli, parents: Set[NodeId], additionalMembers: Set[(UserId, AccessLevel)]): GraphChanges = {

//    val nodeAuthorEdge = Edge.Author(wustAuthorUserId, EdgeData.Author(timestamp), nodeContent.id)
//    val nodeAuthorMemberEdge = Edge.Member(wustAuthorUserId, EdgeData.Member(AccessLevel.ReadWrite), nodeContent.id)

    val parentEdges: Array[Edge] = parents.map(parent => Edge.Child(ParentId(parent), ChildId(nodeContent.id)))(breakOut)
//    val memberEdges: Set[Edge] = additionalMembers.collect {
//      case (member: UserId, access: AccessLevel) => Edge.Member(member, EdgeData.Member(access), nodeContent.id)
//    }


    GraphChanges(
      addNodes = Array(
        nodeContent
      ),
      addEdges = parentEdges
//        ++ memberEdges
//        ++ Array(
//        nodeAuthorEdge, nodeAuthorMemberEdge
//      )
    )
  }

  def editNodeContentInWust(nodeId: NodeId, nodeContent: NodeData.Content): GraphChanges = {
    GraphChanges(
      addNodes = Array(Node.Content(nodeId, nodeContent, NodeRole.Message))
    )
  }

  def createMessageInWust(nodeData: NodeData.Content, wustAuthorUserId: UserId, timestamp: EpochMilli, channel: NodeId, additionalParents: Set[NodeId] = Set.empty, additionalMembers: Set[(UserId, AccessLevel)] = Set.empty): CreationResult = {
    val node = Node.Content(nodeData, NodeRole.Message)
    val message = createNodeInWust(node, wustAuthorUserId, timestamp, additionalParents + channel, additionalMembers)
    CreationResult(node.id, message)
  }

  def editMessageInWust(nodeId: NodeId, nodeData: NodeData.Content, wustAuthorUserId: UserId, timestamp: EpochMilli, channel: NodeId, additionalParents: Set[NodeId] = Set.empty, additionalMembers: Set[(UserId, AccessLevel)] = Set.empty): GraphChanges = {
    val node = Node.Content(nodeId, nodeData, NodeRole.Message)
    val message = createNodeInWust(node, wustAuthorUserId, timestamp, additionalParents + channel, additionalMembers)
    message
  }

  def editMessageContentInWust(nodeId: NodeId, newContent: NodeData.Content): GraphChanges = {
    editNodeContentInWust(nodeId, newContent)
  }

  def deleteMessageInWust(nodeId: NodeId, channelId: NodeId): GraphChanges = {
    GraphChanges.disconnect(Edge.Child)(
      ParentId(channelId),
      ChildId(nodeId),
    )
  }

  def unDeleteMessageInWust(nodeId: NodeId, channelId: NodeId): GraphChanges = {
    GraphChanges.connect(Edge.Child)(
      ParentId(channelId),
      ChildId(nodeId),
    )
  }

  def createChannelInWust(nodeData: NodeData.Content, wustAuthorUserId: UserId, timestamp: EpochMilli, teamNodeId: NodeId, additionalParents: Set[NodeId] = Set.empty, additionalMembers: Set[(UserId, AccessLevel)] = Set.empty): CreationResult = {
    val node = Node.Content(nodeData, NodeRole.Message, NodeMeta(NodeAccess.Inherited))
    val message = createNodeInWust(node, wustAuthorUserId, timestamp, additionalParents + teamNodeId, additionalMembers)
    CreationResult(node.id, message)
  }

  def createWorkspaceInWust(nodeData: NodeData.Content, wustAuthorUserId: UserId, timestamp: EpochMilli, additionalParents: Set[NodeId] = Set.empty, additionalMembers: Set[(UserId, AccessLevel)] = Set.empty): CreationResult = {
    val node = Node.Content(nodeData, NodeRole.Message, NodeMeta(NodeAccess.Restricted))
    val message = createNodeInWust(node, wustAuthorUserId, timestamp, additionalParents, additionalMembers)
    CreationResult(node.id, message)
  }

  def editChannelInWust(nodeId: NodeId, newName: NodeData.Content): GraphChanges = {
    editNodeContentInWust(nodeId, newName)
  }

  def deleteChannelInWust(channelId: NodeId, workspaceNodeId: NodeId): GraphChanges = {
    GraphChanges.disconnect(Edge.Child)(
      ParentId(workspaceNodeId),
      ChildId(channelId),
    )
  }

  def archiveChannelInWust(channelId: NodeId, workspaceNodeId: NodeId, timestamp: EpochMilli): GraphChanges = {
    GraphChanges(
      addEdges = Array(Edge.Child.delete(ParentId(workspaceNodeId), timestamp, ChildId(channelId)))
    )
  }

  def unDeleteChannelInWust(channelId: NodeId, workspaceNodeId: NodeId): GraphChanges = {
    GraphChanges.connect(Edge.Child)(
      ParentId(workspaceNodeId),
      ChildId(channelId),
    )
  }

  def unArchiveChannelInWust(channelId: NodeId, workspaceNodeId: NodeId): GraphChanges = {
    GraphChanges(
      addEdges = Array(Edge.Child(ParentId(workspaceNodeId), ChildId(channelId)))
    )
  }



//    def createMessageInSlack(node: Node) = ???

}
