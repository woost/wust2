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
  val default = NodeMeta(NodeAccess.Inherited)
}

sealed trait Node {
  def id: NodeId
  def data: NodeData
  def role: NodeRole
  def meta: NodeMeta

  @inline def str:String = data.str
  @inline def tpe:String = data.tpe
}

object Node {
  // TODO: we cannot set the nodemeta here, but there is changeable data in the db class
  case class User(id: UserId, data: NodeData.User, meta: NodeMeta) extends Node {
    @inline def name: String = data.name
    def role: NodeRole = NodeRole.default
  }
  case class Content(id: NodeId, data: NodeData.Content, role: NodeRole, meta: NodeMeta) extends Node
  object Content {
    @inline def apply(data: NodeData.Content, role: NodeRole, meta: NodeMeta): Content = {
      new Content(NodeId.fresh, data, role, meta)
    }
    @inline def apply(id: NodeId, data: NodeData.Content, role: NodeRole): Content = {
      new Content(id, data, role, NodeMeta.default)
    }
    @inline def apply(data: NodeData.Content, role:NodeRole): Content = {
      new Content(NodeId.fresh, data, role, NodeMeta.default)
    }
  }
  @inline def MarkdownMessage(text: String) = Content(data = NodeData.Markdown(text), role = NodeRole.Message)
  @inline def MarkdownTask(text: String) = Content(data = NodeData.Markdown(text), role = NodeRole.Task)
  @inline def MarkdownStage(text: String) = Content(data = NodeData.Markdown(text), role = NodeRole.Stage)

  implicit def AsUserInfo(user: User): UserInfo =
    UserInfo(user.id, user.data.name)
}
