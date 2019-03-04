package wust.webApp.views

import wust.graph.{Edge, Graph, Node}
import wust.ids.NodeId

import scala.collection.breakOut

object PropertyData {

  case class PropertyValue(edge: Edge.LabeledProperty, node: Node.Content)
  case class PropertyGroupValue(nodeId: NodeId, values: Array[PropertyValue])
  case class SingleProperty(key: String, values: Array[PropertyValue])
  case class GroupProperty(key: String, groups: Array[PropertyGroupValue])

  case class BasicInfo(node: Node, tags: Array[Node.Content], assignedUsers: Array[Node.User], propertyMap: Map[String, Array[PropertyValue]]) {
    def isEmpty = tags.isEmpty && assignedUsers.isEmpty && propertyMap.isEmpty
  }
  object BasicInfo {
    def apply(graph: Graph, nodeIdx: Int): BasicInfo = {
      val node: Node = graph.nodes(nodeIdx)
      val tags: Array[Node.Content] = graph.tagParentsIdx.map(nodeIdx)(idx => graph.nodes(idx).asInstanceOf[Node.Content]).sortBy(_.data.str)
      val assignedUsers: Array[Node.User] = graph.assignedUsersIdx.map(nodeIdx)(idx => graph.nodes(idx).asInstanceOf[Node.User])
      val properties: Map[String, Array[PropertyValue]] = graph.propertiesEdgeIdx.map(nodeIdx) { idx =>
        val edge = graph.edges(idx).asInstanceOf[Edge.LabeledProperty]
        PropertyValue(edge, graph.nodesById(edge.propertyId).asInstanceOf[Node.Content])
      }.groupBy(_.edge.data.key)

      new BasicInfo(node, tags, assignedUsers, properties)
    }
  }

  case class Single(info: BasicInfo, properties: Array[SingleProperty])
  object Single {
    def apply(graph: Graph, nodeIdx: Int): Single = {
      val info = BasicInfo(graph, nodeIdx)
      val properties: Array[SingleProperty] = info.propertyMap.map { case (key, values) => SingleProperty(key, values)}(breakOut)

      new Single(info, properties.sortBy(_.key.toLowerCase))
    }
  }

  case class Group(infos: Array[BasicInfo], properties: Array[GroupProperty])
  object Group {
    def apply(graph: Graph, childrenIdxs: Array[Int]): Group = {
      val infos = childrenIdxs.map(BasicInfo(graph, _))
      val allProperties: Array[String] = infos.flatMap(_.propertyMap.keys).distinct.sorted
      val groupProperties: Array[GroupProperty] = allProperties.map { propertyKey =>
        GroupProperty(propertyKey, infos.map { info =>
          PropertyGroupValue(info.node.id, info.propertyMap.getOrElse(propertyKey, Array()))
        })
      }

      Group(infos, groupProperties)
    }
  }
}
