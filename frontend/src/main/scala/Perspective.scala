package wust.frontend

import wust.graph._

case class Perspective(collapsed: Selector = Selector.Nothing) {
  def intersect(that: Perspective) = copy(collapsed = this.collapsed intersect that.collapsed)
  def union(that: Perspective) = copy(collapsed = this.collapsed union that.collapsed)

  def applyOnGraph(graph: Graph) = Collapse(collapsed)(DisplayGraph(graph))
}
