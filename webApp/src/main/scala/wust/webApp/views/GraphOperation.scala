package wust.webApp.views

import wust.util.algorithm.dfs
import cats.Eval
import flatland.ArraySet
import wust.graph.{ Edge, Graph }
import wust.ids.{ NodeId, NodeRole, UserId }
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

  @inline private def noBoundaryRole(nodeIdx: Int, graph: Graph) = InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task, NodeRole.Note)(graph.nodes(nodeIdx).role)
  @inline private def isBoundaryRole(nodeIdx: Int, graph: Graph) = !noBoundaryRole(nodeIdx, graph)

  @inline private def foreachParentsAndChildrenInRoleBoundary(startIdx: Int, action: Int => Unit, graph: Graph): Unit = {
    //TODO: why do we have to use notDeletedParentsIdx and notDeletedChildrenIdx here? shouldn't this be already handled by another filter?
    dfs.foreachStopLocally(_(startIdx), dfs.withStart, graph.notDeletedParentsIdx, continue = { nodeIdx =>
      val roleIsAllowed = noBoundaryRole(nodeIdx, graph)
      if (roleIsAllowed) action(nodeIdx)
      roleIsAllowed
    })
    dfs.foreachStopLocally(_(startIdx), dfs.afterStart, graph.notDeletedChildrenIdx, continue = { nodeIdx =>
      val roleIsAllowed = noBoundaryRole(nodeIdx, graph)
      if (roleIsAllowed) action(nodeIdx)
      roleIsAllowed
    })
  }

  final case class OnlyTaggedWith(tagId: NodeId) extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      pageIdx.flatMap { _ =>
        graph.idToIdx(tagId).map { tagIdx => (_, edgeIdx) =>
          val childIdx = graph.edgesIdx.b(edgeIdx)

          def anyParentOrChildInBoundaryIsTaggedByTagOrSubtag = dfs.exists(foreachParentsAndChildrenInRoleBoundary(childIdx, _, graph), dfs.withStart, graph.notDeletedParentsIdx, isFound = _ == tagIdx)
          isBoundaryRole(childIdx, graph) || anyParentOrChildInBoundaryIsTaggedByTagOrSubtag
        }
      }
    }
  }

  case object OnlyDeletedChildren extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      pageIdx.map { _ => (_, edgeIdx) =>
        val parentIdx = graph.edgesIdx.a(edgeIdx)
        val childIdx = graph.edgesIdx.b(edgeIdx)
        graph.isDeletedNowIdx(childIdx, parentIdx)
      }
    }
  }

  case object ExcludeDeletedChildren extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      pageIdx.map { _ => (_, edgeIdx) =>
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

  final case class OnlyAssignedTo(userId: UserId) extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      Some({ (_, edgeIdx) =>
        val childIdx = graph.edgesIdx.b(edgeIdx)

        def anyParentIsAssigned = dfs.exists(_(childIdx), dfs.withStart, graph.notDeletedParentsIdx,
          isIncluded = { nodeIdx => noBoundaryRole(nodeIdx, graph) },
          isFound = { nodeIdx => graph.assignedUsersIdx.contains(nodeIdx)(graph.idToIdxOrThrow(userId)) })

        def anyChildIsAssigned = dfs.exists(_(childIdx), dfs.afterStart, graph.notDeletedChildrenIdx,
          isIncluded = { nodeIdx => noBoundaryRole(nodeIdx, graph) },
          isFound = { nodeIdx => graph.assignedUsersIdx.contains(nodeIdx)(graph.idToIdxOrThrow(userId)) })

        isBoundaryRole(childIdx, graph) || anyParentIsAssigned || anyChildIsAssigned
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
