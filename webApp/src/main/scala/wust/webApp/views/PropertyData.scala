package wust.webApp.views

import wust.graph.{Edge, Graph, Node}
import wust.util.collection.BasicMap

//TODO: separate calculations into separate rx: rx for tags, stages, users, properties
//TODO: use ids instead of nodes and listen to node changes in each rendered node
object PropertyData {

  final case class PropertyValue(edge: Edge.LabeledProperty, node: Node.Content)
  final case class PropertyGroupValue(node: Node, values: List[PropertyValue])
  final case class SingleProperty(key: String, values: List[PropertyValue])
  final case class GroupProperty(key: String, groups: Seq[PropertyGroupValue])

  final case class BasicInfo(node: Node, tags: Seq[Node.Content], stages: Seq[Node.Content], assignedUsers: Seq[Node.User], propertyMap: BasicMap[String, List[PropertyValue]], reverseProperties: Seq[Node]) {
    def isEmpty = tags.isEmpty && assignedUsers.isEmpty && propertyMap.isEmpty
  }

  def getProperties(graph: Graph, nodeIdx: Int):BasicMap[String,List[PropertyValue]] = {
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
    properties
  }

  object BasicInfo {
    def apply(graph: Graph, nodeIdx: Int): BasicInfo = {
      val node: Node = graph.nodes(nodeIdx)
      val tags: Array[Node.Content] = graph.tagParentsIdx.map(nodeIdx)(idx => graph.nodes(idx).as[Node.Content]).sortBy(_.data.str)
      val stages: Array[Node.Content] = graph.stageParentsIdx.map(nodeIdx)(idx => graph.nodes(idx).as[Node.Content]).sortBy(_.data.str)
      val assignedUsers: Array[Node.User] = graph.assignedUsersIdx.map(nodeIdx)(idx => graph.nodes(idx).as[Node.User])
      val properties = getProperties(graph, nodeIdx)
      val reverseProperties: Array[Node] = graph.propertiesEdgeReverseIdx.map(nodeIdx) { idx =>
        val nodeIdx = graph.edgesIdx.a(idx)
        graph.nodes(nodeIdx)
      }

      new BasicInfo(node, tags, stages, assignedUsers, properties, reverseProperties)
    }
  }

  final case class Single(info: BasicInfo, properties: Seq[SingleProperty]) {
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

  final case class Group(infos: Seq[BasicInfo], properties: Seq[GroupProperty])
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
