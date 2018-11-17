package wust.graph

import wust.ids._

case class Page(parentId: Option[NodeId]) extends AnyVal {
  @inline def isEmpty: Boolean = parentId.isEmpty
  @inline def isDefined: Boolean = parentId.isDefined
}

object Page {
  @inline def apply(parentId: NodeId): Page = Page(Some(parentId))
  @inline def empty: Page = Page(None)
}
