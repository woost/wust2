package wust.webApp.state

import wust.graph._
import wust.ids.NodeId
import wust.util.Selector

case class Perspective(collapsed: Selector[NodeId] = Selector.None[NodeId]) {
  def intersect(that: Perspective) = copy(collapsed = this.collapsed intersect that.collapsed)
  def union(that: Perspective) = copy(collapsed = this.collapsed union that.collapsed)

  def applyOnGraph(graph: Graph) = Collapse(collapsed)(CollapsedGraph(graph))
}
