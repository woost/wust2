package wust.utilWeb

import wust.graph._
import wust.util.Selector
import wust.ids._

case class Perspective(collapsed: Selector[PostId] = Selector.None[PostId]) {
  def intersect(that: Perspective) = copy(collapsed = this.collapsed intersect that.collapsed)
  def union(that: Perspective) = copy(collapsed = this.collapsed union that.collapsed)

  def applyOnGraph(graph: Graph) = Collapse(collapsed)(DisplayGraph(graph))
}
