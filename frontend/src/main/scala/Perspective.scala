package wust.frontend

import wust.graph._
import wust.util.Pipe

case class Perspective(
  collapsed: Selector = Selector.Nothing
) {
  def intersect(that: Perspective) = copy(collapsed = this.collapsed intersect that.collapsed)
  def union(that: Perspective) = copy(collapsed = this.collapsed union that.collapsed)
}

object Perspective {
  def apply(view: Perspective, graph: Graph) = Collapse(view.collapsed)(DisplayGraph(graph))
}
