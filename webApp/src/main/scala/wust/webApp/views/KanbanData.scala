package wust.webApp.views

import flatland._
import wust.graph._
import wust.ids._
import wust.util.algorithm.dfs
import wust.util.collection._
import wust.util.macros.InlineList
import wust.webApp.state.TraverseState

import scala.collection.mutable

object KanbanData {
  case class Config(groupAttribute: Entity.Attribute, contentRole: NodeRole)
  object Config {
    def apply(graph: Graph, nodeId: Int, groupKey: PropertyKey, contentRole: NodeRole): Config = {
      val entity = Entity(Nil) //TODO: get from somewhere
      def defaultAttribute = Entity.Attribute(groupKey, NodeTypeSelection.DeepChildrenChain(NodeRole.Stage))
      val attribute = entity.attributes.find(_.key == groupKey).getOrElse(defaultAttribute)
      Config(attribute, contentRole)
    }
  }

  sealed trait Kind
  object Kind {
    case object Content extends Kind
    case object Group extends Kind
  }

  def inboxNodes(graph: Graph, traverseState: TraverseState, config: Config): Seq[NodeId] = graph.idToIdxFold(traverseState.parentId)(Seq.empty[NodeId]) { parentIdx =>

    val inboxTasks = Array.newBuilder[Int]
    graph.childrenIdx.foreachElement(parentIdx) { childIdx =>
      val node = graph.nodes(childIdx)
      if(node.role == config.contentRole && !traverseState.contains(node.id)) {
        @inline def hasStage = graph.propertiesEdgeIdx.exists(childIdx) { edgeIdx =>
          val edge = graph.edges(edgeIdx).as[Edge.LabeledProperty]
          val propertyNode = graph.nodes(graph.edgesIdx.b(edgeIdx))
          edge.data.key == config.groupAttribute.key && NodeTypeSelector.isSelected(config.groupAttribute.selection)(propertyNode)
        }

        if(!hasStage) inboxTasks += childIdx
      }
    }

    TaskOrdering.constructOrderingOf[NodeId](graph, traverseState.parentId, inboxTasks.result.viewMap(graph.nodeIds), identity)
  }

  def columns(graph: Graph, traverseState: TraverseState, config: Config): Seq[NodeId] = graph.idToIdxFold(traverseState.parentId)(Seq.empty[NodeId]){ parentIdx =>
    val columnIds = mutable.ArrayBuffer[NodeId]()
    graph.childrenIdx.foreachElement(parentIdx) { idx =>
      val node = graph.nodes(idx)
      if (NodeTypeSelector.isSelected(config.groupAttribute.selection)(node) && !traverseState.contains(node.id)) {
        columnIds += node.id
      }
    }

    TaskOrdering.constructOrderingOf[NodeId](graph, traverseState.parentId, columnIds, identity)
  }

  def columnNodes(graph: Graph, traverseState: TraverseState, config: Config): Seq[(NodeId, Kind)] = graph.idToIdxFold(traverseState.parentId)(Seq.empty[(NodeId, Kind)]){ parentIdx =>
    val childrenIds = mutable.ArrayBuffer[(NodeId, Kind)]()
    graph.propertiesEdgeReverseIdx.foreachElement(parentIdx) { edgeIdx =>
      val edge = graph.edges(edgeIdx).as[Edge.LabeledProperty]
      println("REVERSE PROP " + edge)
      if (edge.data.key == config.groupAttribute.key) {
        val contentIdx = graph.edgesIdx.a(edgeIdx)
        val node = graph.nodes(contentIdx)
        println("ISKEY "  + node + traverseState)
        if (!traverseState.contains(node.id)) {
          println("GO?")
          if (NodeTypeSelector.isSelected(config.groupAttribute.selection)(node)) childrenIds += node.id -> Kind.Content
          else if (config.contentRole == node.role) childrenIds += node.id -> Kind.Content
        }
      }
    }

    TaskOrdering.constructOrderingOf[(NodeId, Kind)](graph, traverseState.parentId, childrenIds, { case (id, _) => id })
  }
}
