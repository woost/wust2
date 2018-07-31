package wust.graph

import wust.ids._
import wust.util.Memo
import wust.util.algorithm._
import wust.util.collection._

import collection.mutable
import collection.breakOut

case class NodeMeta(accessLevel: NodeAccess)
object NodeMeta {
  //TODO a user should NOT have NodeMeta. We cannot delete users like normal
  //posts and what does join and accesslevel actually mean in the context of a
  //user?
  def User = NodeMeta(NodeAccess.Level(AccessLevel.Restricted))
}

sealed trait Node {
  def id: NodeId
  def data: NodeData
  def meta: NodeMeta
}

object Node {
  // TODO: we cannot set the nodemeta here, but there is changeable data in the db class
  case class User(id: UserId, data: NodeData.User, meta: NodeMeta) extends Node
  case class Content(id: NodeId, data: NodeData.Content, meta: NodeMeta) extends Node
  object Content {
    private val defaultMeta = NodeMeta(NodeAccess.Inherited)

    def empty = new Content(NodeId.fresh, NodeData.Markdown(""), defaultMeta)

    def apply(id: NodeId, data: NodeData.Content): Content = {
      new Content(id, data, defaultMeta)
    }
    def apply(data: NodeData.Content): Content = {
      new Content(NodeId.fresh, data, defaultMeta)
    }
  }

  implicit def AsUserInfo(user: User): UserInfo =
    UserInfo(user.id, user.data.name, user.data.channelNodeId)
}
