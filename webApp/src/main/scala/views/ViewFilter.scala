package wust.webApp.views

import wust.graph.{Edge, Graph}
import wust.ids.{NodeId, UserId}

object ViewFilter {

  object Parents {

    def withoutDeletedParents(pageId: NodeId)(graph: Graph): Graph = {
      val newEdges = graph.edges.filterNot {
        case e: Edge.Parent => e.targetId == pageId && graph.isDeletedNow(e.sourceId, Seq(pageId))
        case _ => false
      }
      graph.copy(edges = newEdges)
    }

    def onlyDeletedParents(pageId: NodeId)(graph: Graph): Graph = {
      val newEdges = graph.edges.filter {
        case e: Edge.Parent => e.targetId == pageId && graph.isDeletedNow(e.sourceId, Seq(pageId))
        case _ => true
      }
      graph.copy(edges = newEdges)
    }

  }

  object Assignments {

    def onlyAssignedTo(userId: UserId)(graph: Graph): Graph = {
      val nodeIds = graph.edges.collect {
        case e: Edge.Assigned if e.sourceId == userId => e.targetId
      }
      val newEdges = graph.edges.filter {
        case e: Edge.Parent => nodeIds.contains(e.sourceId) || nodeIds.contains(e.targetId)
        case _ => true
      }
      graph.copy(edges = newEdges)
    }

    def onlyNotAssigned(graph: Graph): Graph = {
      val nodeIds = graph.edges.collect {
        case e: Edge.Assigned => e.targetId
      }
      val newEdges = graph.edges.filterNot {
        case e: Edge.Parent => nodeIds.contains(e.sourceId) || nodeIds.contains(e.targetId)
        case _ => false
      }
      graph.copy(edges = newEdges)
    }

  }
}
