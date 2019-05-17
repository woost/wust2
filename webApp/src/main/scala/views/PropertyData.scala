package wust.webApp.views

import wust.graph.{Edge, Graph, Node}
import wust.ids.NodeId
import wust.util.collection.BasicMap

import scala.collection.breakOut

//TODO: We should not use Array here, because scala.rx cannot do equality on them (when streaming property data)
//TODO: separate calculations into separate rx: rx for tags, stages, users, properties
//TODO: use ids instead of nodes and listen to node changes in each rendered node
object PropertyData {

  case class PropertyValue(edge: Edge.LabeledProperty, node: Node.Content)
  case class PropertyGroupValue(node: Node, values: List[PropertyValue])
  case class SingleProperty(key: String, values: List[PropertyValue])
  case class GroupProperty(key: String, groups: Array[PropertyGroupValue])

  case class BasicInfo(node: Node, tags: Array[Node.Content], stages: Array[Node.Content], assignedUsers: Array[Node.User], propertyMap: BasicMap[String, List[PropertyValue]], reverseProperties: Array[Node]) {
    def isEmpty = tags.isEmpty && assignedUsers.isEmpty && propertyMap.isEmpty
  }
  object BasicInfo {
    def apply(graph: Graph, nodeIdx: Int): BasicInfo = {
      val node: Node = graph.nodes(nodeIdx)
      val tags: Array[Node.Content] = graph.tagParentsIdx.map(nodeIdx)(idx => graph.nodes(idx).as[Node.Content]).sortBy(_.data.str)
      val stages: Array[Node.Content] = graph.stageParentsIdx.map(nodeIdx)(idx => graph.nodes(idx).as[Node.Content]).sortBy(_.data.str)
      val assignedUsers: Array[Node.User] = graph.assignedUsersIdx.map(nodeIdx)(idx => graph.nodes(idx).as[Node.User])
      val properties = BasicMap.ofString[List[PropertyValue]]()
      graph.propertiesEdgeIdx.foreachElement(nodeIdx) { idx =>
        val edge = graph.edges(idx).as[Edge.LabeledProperty]
        val value = PropertyValue(edge, graph.nodesByIdOrThrow(edge.propertyId).as[Node.Content])
        properties.get(edge.data.key).fold {
          properties += edge.data.key -> List(value)
        } { props =>
          properties += edge.data.key -> (value :: props)
        }
      }
      val reverseProperties: Array[Node] = graph.propertiesEdgeReverseIdx.map(nodeIdx) { idx =>
        val nodeIdx = graph.edgesIdx.a(idx)
        graph.nodes(nodeIdx)
      }

      new BasicInfo(node, tags, stages, assignedUsers, properties, reverseProperties)
    }
  }

  case class Single(info: BasicInfo, properties: Array[SingleProperty]) {
    def isEmpty = info.isEmpty && properties.isEmpty
  }
  object Single {
    def apply(graph: Graph, nodeIdx: Int): Single = {
      val info = BasicInfo(graph, nodeIdx)
      val properties = {
        val arr = new Array[SingleProperty](info.propertyMap.size)
        var i = 0
        info.propertyMap.foreach { (key, values) =>
          arr(i) = SingleProperty(key, values)
          i += 1
        }
        arr
      }

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
          PropertyGroupValue(info.node, info.propertyMap.getOrElse(propertyKey, Nil))
        })
      }

      Group(infos, groupProperties)
    }
  }
}
