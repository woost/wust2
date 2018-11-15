package wust.graph

import wust.ids._

sealed trait Page {
  def parentIds: Seq[NodeId]
  def childrenIds: Seq[NodeId]

  def copy(parentIds: Seq[NodeId] = parentIds, childrenIds: Seq[NodeId] = childrenIds): Page.Selection = {
    Page.Selection(parentIds, childrenIds)
  }

  lazy val parentIdSet = parentIds.toSet
}
object Page {
  case class Selection(parentIds: Seq[NodeId], childrenIds: Seq[NodeId]) extends Page
  case class NewChanges(nodeId: Option[NodeId], extraChanges: GraphChanges) extends Page {
    override def parentIds = nodeId.toList
    override def childrenIds = Nil
  }

  def apply(
    parentIds: Seq[NodeId],
    childrenIds: Seq[NodeId] = Nil,
  ): Page = Selection(parentIds, childrenIds)

  def unapply(page: Page): Option[(Seq[NodeId], Seq[NodeId])] = Some((page.parentIds, page.childrenIds))

  val empty: Page = Page(Seq.empty)
  def apply(parentId: NodeId): Page = apply(Seq(parentId))
}
