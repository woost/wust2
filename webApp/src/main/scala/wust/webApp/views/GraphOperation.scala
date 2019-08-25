package wust.webApp.views

import cats.Eval
import flatland.ArraySet
import wust.graph.{Edge, Graph}
import wust.ids.{NodeId, NodeRole, UserId}
import wust.util.macros.InlineList
import wust.webApp.search.Search

sealed trait UserViewGraphTransformation {
  def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): GraphOperation.EdgeFilter
}

object GraphOperation {
  type EdgeFilter = Option[(Edge.Child, Int) => Boolean] // (edge, edgeidx) => boolean

  def filter(graph: Graph, pageId: NodeId, userId: UserId, filters: Seq[UserViewGraphTransformation]): Graph = {
    val pageIdx = graph.idToIdx(pageId)
    val userIdx = graph.idToIdxOrThrow(userId)
    val edgeFilters: Seq[(Edge.Child, Int) => Boolean] = filters.flatMap(_.filterWithViewData(pageIdx, userIdx, graph))
    if (edgeFilters.isEmpty) graph
    else graph.filterChildEdges((e, i) => edgeFilters.forall(_(e, i)))
  }

  final case class OnlyTaggedWith(tagId: NodeId) extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      pageIdx.flatMap { _ =>
        graph.idToIdx(tagId).map { tagIdx =>
          (_, edgeIdx) =>
            val childIdx = graph.edgesIdx.b(edgeIdx)
            val node = graph.nodes(childIdx)
            !InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task)(node.role) ||
              graph.tagParentsIdx.contains(childIdx)(tagIdx) ||
              graph.descendantsIdxExists(tagIdx)(_ == childIdx)
        }
      }
    }
  }

  case object OnlyDeletedChildren extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      pageIdx.map { _ =>
        (_, edgeIdx) =>
          val parentIdx = graph.edgesIdx.a(edgeIdx)
          val childIdx = graph.edgesIdx.b(edgeIdx)
          graph.isDeletedNowIdx(childIdx, parentIdx)
      }
    }
  }

  case object ExcludeDeletedChildren extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      pageIdx.map { _ =>
        (_, edgeIdx) =>
          val parentIdx = graph.edgesIdx.a(edgeIdx)
          val childIdx = graph.edgesIdx.b(edgeIdx)
          !graph.isDeletedNowIdx(childIdx, parentIdx)
      }
    }
  }

  case object AutomatedHideTemplates extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      Some({ (_, edgeIdx) =>
        val childIdx = graph.edgesIdx.b(edgeIdx)
        graph.automatedEdgeReverseIdx.sliceIsEmpty(childIdx)
      })
    }
  }

  case object OnlyAssignedTo extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      Some({ (_, edgeIdx) =>
        val childIdx = graph.edgesIdx.b(edgeIdx)
        graph.nodes(childIdx).role != NodeRole.Task || graph.assignedUsersIdx.contains(childIdx)(userIdx)
      })
    }
  }

  case object OnlyNotAssigned extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      Some({ (_, edgeIdx) =>
        val childIdx = graph.edgesIdx.b(edgeIdx)
        graph.nodes(childIdx).role != NodeRole.Task || graph.assignedUsersIdx.sliceIsEmpty(childIdx)
      })
    }
  }

  case object Identity extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      None
    }
  }

  final case class ContentContains(needle: String) extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      //TODO better without descendants? one dfs?

      val foundChildrenEval = Eval.later{
        val foundChildren = ArraySet.create(graph.nodes.length)
        flatland.loop(graph.nodes.length) { nodeIdx =>
          if (graph.parentsIdx.sliceNonEmpty(nodeIdx) && Search.singleByString(needle, graph.nodes(nodeIdx), 0.75).isDefined) {
            foundChildren += nodeIdx
          }
        }
        foundChildren
      }

      pageIdx.map { _ =>
        val foundChildren = foundChildrenEval.value

        { (_, edgeIdx) =>
          val childIdx = graph.edgesIdx.b(edgeIdx)
          if (foundChildren.contains(childIdx)) true
          else {
            val hasDescendant = graph.descendantsIdxExists(childIdx)(foundChildren.contains)
            if (hasDescendant) foundChildren += childIdx // cache the result of this partial dfs
            hasDescendant
          }
        }
      }
    }
  }
}
