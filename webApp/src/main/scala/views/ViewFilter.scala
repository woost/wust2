package wust.webApp.views

import wust.graph.{Edge, Graph}
import wust.ids.NodeId

object ViewFilter {

  object Parents {

    def withoutDeletedParents(pageId: NodeId)(graph: Graph): Graph = {
      val newEdges = graph.edges.filterNot{
        case e: Edge.Parent => e.targetId == pageId && graph.isDeletedNow(e.sourceId, Seq(pageId))
        case _ => false
      }
      val filter = graph.copy(edges = newEdges)
      filter
    }

    def onlyDeletedParents(pageId: NodeId)(graph: Graph): Graph = {
      val newEdges = graph.edges.filter{
        case e: Edge.Parent => e.targetId == pageId && graph.isDeletedNow(e.sourceId, Seq(pageId))
        case _ => true
      }
      val filter = graph.copy(edges = newEdges)
      filter
    }

  }
}
