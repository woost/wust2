package wust.graph

import wust.ids._

case class Page(parentIds: Set[PostId]) {
  def add(parentId: PostId) = copy(parentIds + parentId)
  def remove(parentId: PostId) = copy(parentIds - parentId)
}

object Page {
  val empty = Page(Set.empty)
}



