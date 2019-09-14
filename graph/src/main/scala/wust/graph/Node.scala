package wust.graph

import wust.ids._

final case class NodeMeta(accessLevel: NodeAccess)
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
  def schema: NodeSchema

  def views = schema.views
  def entities = schema.entities

  @inline def as[T <: Node]: T = asInstanceOf[T]

  @inline def str: String = data.str
  @inline def tpe: String = data.tpe
}

object Node {
  // TODO: we cannot set the nodemeta here, but there is changeable data in the db class
  final case class User(id: UserId, data: NodeData.User, meta: NodeMeta, schema: NodeSchema = NodeSchema.empty) extends Node {
    @inline def name: String = data.name
    def role: NodeRole = NodeRole.default
    override def toString = s"""User([${id.shortHumanReadable}]"$name"${if (data.isImplicit) ":implicit" else ""}${if (meta.accessLevel != NodeAccess.Restricted) s":$meta" else ""}  ${id.toBase58}  ${id.toUuid})"""
  }
  final case class Content(id: NodeId, data: NodeData.Content, role: NodeRole, meta: NodeMeta, schema: NodeSchema = NodeSchema.empt) extends Node
  object Content {
    @inline def apply(data: NodeData.Content, role: NodeRole, meta: NodeMeta): Content = {
      new Content(NodeId.fresh, data, role, meta)
    }
    @inline def apply(id: NodeId, data: NodeData.Content, role: NodeRole): Content = {
      new Content(id, data, role, NodeMeta.default)
    }
    @inline def apply(data: NodeData.Content, role: NodeRole): Content = {
      new Content(NodeId.fresh, data, role, NodeMeta.default)
    }
  }
  @inline def MarkdownMessage(text: String) = Content(data = NodeData.Markdown(text), role = NodeRole.Message)
  @inline def MarkdownTask(text: String) = Content(data = NodeData.Markdown(text), role = NodeRole.Task)
  @inline def MarkdownTag(text: String) = Content(data = NodeData.Markdown(text), role = NodeRole.Tag)
  @inline def MarkdownStage(text: String) = Content(data = NodeData.Markdown(text), role = NodeRole.Stage)
  @inline def MarkdownProject(text: String) = Content(data = NodeData.Markdown(text), role = NodeRole.Project)
  @inline def MarkdownNote(text: String) = Content(data = NodeData.Markdown(text), role = NodeRole.Note)

  implicit def AsUserInfo(user: User): UserInfo =
    UserInfo(user.id, user.data.name)
}
