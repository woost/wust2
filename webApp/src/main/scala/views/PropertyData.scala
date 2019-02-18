package wust.webApp.views

import wust.graph.{Edge, Graph, Node}
import wust.ids.NodeId

import scala.collection.breakOut

object PropertyData {
  case class PropertyValue(edge: Edge.LabeledProperty, node: Node.Content)
  case class PropertyGroupValue(nodeId: NodeId, values: Array[PropertyValue])
  case class GroupProperty(key: String, groups: Array[PropertyGroupValue])
  case class SingleProperty(key: String, values: Array[PropertyValue])

  case class Single(node: Node, tags: Array[Node.Content], assignedUsers: Array[Node.User], properties: Array[SingleProperty])
  object Single {
    def apply(graph: Graph, nodeIdx: Int): Single = {
      val node: Node = graph.nodes(nodeIdx)
      val tags: Array[Node.Content] = graph.tagParentsIdx.map(nodeIdx)(idx => graph.nodes(idx).asInstanceOf[Node.Content])
      val assignedUsers: Array[Node.User] = graph.assignedUsersIdx.map(nodeIdx)(idx => graph.nodes(idx).asInstanceOf[Node.User])
      val properties: Array[SingleProperty] = graph.propertiesEdgeIdx.map(nodeIdx)(idx => graph.edges(idx).asInstanceOf[Edge.LabeledProperty]).groupBy(_.data.key).map { case (key, edges) =>
        SingleProperty(key, edges.map(edge => PropertyValue(edge, graph.nodesById(edge.propertyId).asInstanceOf[Node.Content])))
      }(breakOut)

      new Single(node, tags, assignedUsers, properties.sortBy(_.key))
    }

  }
  case class Group(data: Array[Single], properties: Array[GroupProperty])
  object Group {
    def apply(graph: Graph, childrenIdxs: Array[Int]): Group = {
      val data: Array[Single] = childrenIdxs.map(Single(graph, _))
      val properties: Array[GroupProperty] = data.flatMap(d => d.properties.map(d.node.id -> _)).groupBy { case (_, p) => p.key }.map { case (key, properties) =>
        GroupProperty(key, properties.map { case (nodeId, property) => PropertyGroupValue(nodeId, property.values)})
      }(breakOut)

      Group(data, properties.sortBy(_.key))
    }
  }
}
