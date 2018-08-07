package wust.graph

import wust.ids._

sealed trait PageMode { val name = toString.toLowerCase }
object PageMode {
  case object Default extends PageMode
  case object Orphans extends PageMode
}
sealed trait Page {
  def parentIds: Seq[NodeId]
  def childrenIds: Seq[NodeId]
  def mode: PageMode

  def copy(parentIds: Seq[NodeId] = parentIds, childrenIds: Seq[NodeId] = childrenIds, mode: PageMode = mode): Page.Selection = {
    Page.Selection(parentIds, childrenIds, mode)
  }

  lazy val parentIdSet = parentIds.toSet
}
object Page {
  case class Selection(parentIds: Seq[NodeId], childrenIds: Seq[NodeId], mode: PageMode) extends Page
  case class NewChannel(nodeId: NodeId) extends Page {
    override def parentIds = nodeId :: Nil
    override def childrenIds = Nil
    override def mode = PageMode.Default
  }

  def apply(
    parentIds: Seq[NodeId],
    childrenIds: Seq[NodeId] = Nil,
    mode: PageMode = PageMode.Default
  ): Selection = Selection(parentIds, childrenIds, mode)

  def unapply(page: Page): Option[(Seq[NodeId], Seq[NodeId], PageMode)] = Some((page.parentIds, page.childrenIds, page.mode))

  val empty: Selection = Page(Seq.empty)
  def ofUser(user: UserInfo): Selection = apply(Seq(user.channelNodeId))
  def apply(parentId: NodeId): Selection = apply(Seq(parentId))
}
