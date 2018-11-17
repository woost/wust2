package wust.graph

import wust.ids._

sealed trait Page {
  def parentId: Option[NodeId]
}

object Page {
  case class Selection(nodeId: NodeId) extends Page {
    override def parentId: Option[NodeId] = Some(nodeId)
  }
  case class NewChanges(parentId: Option[NodeId], extraChanges: GraphChanges) extends Page

  case object Empty extends Page {
    @inline override def parentId: Option[NodeId] = None
  }

  def apply(parentId: NodeId): Page = Selection(parentId)
  def unapply(page: Page): Option[NodeId] = page.parentId

  @inline def empty: Page = Empty
}
