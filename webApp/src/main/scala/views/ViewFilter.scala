package wust.webApp.views

import fomanticui.SidebarOptions
import googleAnalytics.Analytics
import jquery.JQuerySelection
import monix.reactive.Observable
import monix.reactive.subjects.BehaviorSubject
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx.Var.Assignment
import rx.{Ctx, Rx, Var}
import supertagged.TaggedType
import wust.css.ZIndex
import wust.graph.{Edge, Graph, Node}
import wust.ids.{NodeId, NodeRole, UserId}
import wust.util.algorithm
import wust.util.macros.{InlineList, SubObjects}
import wust.webApp.Icons
import wust.webApp.state.GlobalState
import wust.webApp.outwatchHelpers._
import wust.webApp.views.GraphOperation.{GraphFilter, GraphTransformation}

import scala.collection.breakOut

object ViewFilter {

  private def allTransformations(state: GlobalState)(implicit ctx: Ctx.Owner): List[ViewGraphTransformation] = List(
//    ViewGraphTransformation.Deleted.inGracePeriod(state),
    ViewGraphTransformation.Deleted.onlyDeleted(state),
    ViewGraphTransformation.Deleted.noDeleted(state),
    ViewGraphTransformation.Deleted.noDeletedButGraced(state),
    ViewGraphTransformation.Assignments.onlyAssignedTo(state),
    ViewGraphTransformation.Assignments.onlyNotAssigned(state),
    ViewGraphTransformation.Automated.hideTemplates(state),
    //    Identity(state),
  )

  def renderMenu(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    val filterTransformations: Seq[ViewGraphTransformation] = allTransformations(state)
    val filterColor = state.isFilterActive.map(active => if(active) VDomModifier( color := "green" ) else VDomModifier.empty)

    UI.accordion(
      title = VDomModifier(
        filterColor,
        span("Filter"),
      ),
      content = div(
        Components.verticalMenu(
          filterTransformations.map { transformation =>
            Components.MenuItem(
              title = transformation.icon,
              description = transformation.description,
              active = state.graphTransformations.map(_.contains(transformation.transform)),
              clickAction = { () =>
                state.graphTransformations.update { transformations =>
                  if (transformations.contains(transformation.transform)) transformations.filter(_ != transformation.transform)
                  else transformations :+ transformation.transform
                }
                Analytics.sendEvent("filter", transformation.toString)
              }
            )
          }
        ),
        div(
          cursor.pointer,
          Elements.icon(Icons.noFilter),
          span("Reset ALL filters"),
          onClick(Seq.empty[UserViewGraphTransformation]) --> state.graphTransformations,
          onClick foreach { Analytics.sendEvent("filter", "reset") },
        )
      ),
    ).prepend(
      Elements.icon(Icons.filter),
    )
  }

  def addLabeledFilterCheckbox(state: GlobalState, filterName: String, header: VDomModifier, description: VDomModifier, transform: UserViewGraphTransformation)(implicit ctx: Ctx.Owner): VNode = {
    val checkbox = addFilterCheckbox(state, filterName, transform)

    div(
      cls := "item",
      div(
        cls := "ui checkbox toggle",
        checkbox,
      ),
      header,
      description
    )
  }

  def addFilterCheckbox(state: GlobalState, filterName: String, transform: UserViewGraphTransformation)(implicit ctx: Ctx.Owner): VNode = {
    val activeFilter = (doActivate: Boolean) =>  if(doActivate) {
      state.graphTransformations.map(_ :+ transform)
    } else {
      state.graphTransformations.map(_.filter(_ != transform))
    }

    input(tpe := "checkbox",
      onChange.checked.map(v => activeFilter(v).now) --> state.graphTransformations,
      onChange.checked foreach { enabled => if(enabled) Analytics.sendEvent("filter", s"$filterName") },
      checked <-- state.graphTransformations.map(_.contains(transform)),
    )
  }
}


case class ViewGraphTransformation(
  state: GlobalState,
  transform: UserViewGraphTransformation,
  icon: VDomModifier,
  description: String,
)

object ViewGraphTransformation {
  def identity(state: GlobalState) = ViewGraphTransformation(
    state = state,
    icon = Icons.noFilter,
    description = "Reset ALL filters",
    transform = GraphOperation.Identity,
  )

  object Deleted {
    def inGracePeriod(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon  = Icons.delete,
      description = "Show soon auto-deleted items",
      transform = GraphOperation.InDeletedGracePeriodParents,
    )
    def onlyDeleted(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon = Icons.delete,
      description = "Show only deleted items",
      transform = GraphOperation.OnlyDeletedParents,
    )
    def noDeleted(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon = Icons.undelete,
      description = "Hide deleted items",
      transform = GraphOperation.NoDeletedParents,
    )
    def noDeletedButGraced(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon = Icons.undelete,
      description = "Hide older deleted items",
      transform = GraphOperation.NoDeletedButGracedParents,
    )
  }

  object Automated {
    def hideTemplates(state: GlobalState) = ViewGraphTransformation(
      state = state,
      icon = Icons.automate,
      description = s"Hide automation templates",
      transform = GraphOperation.AutomatedHideTemplates,
    )
  }

  object Assignments {
    def onlyAssignedTo(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon = Icons.task,
      description = s"Show items assigned to: Me",
      transform = GraphOperation.OnlyAssignedTo,
    )
    def onlyNotAssigned(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon = Icons.task,
      description = "Show unassigned items",
      transform = GraphOperation.OnlyNotAssigned,
    )
  }

}

sealed trait UserViewGraphTransformation {
  def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter
  def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation =
    GraphOperation.filterToTransformation(filterWithViewData(pageId, userId))
}
object GraphOperation {
  type GraphTransformation = Graph => Graph
  type GraphFilter = Graph => (Graph, Array[Edge])

  def filterToTransformation(graphFilter: GraphFilter): GraphTransformation = {
    graphFilter.andThen {
      case (graph: Graph, newEdges: Array[Edge]) => graph.copy(edges = newEdges)
    }
  }

  def stackFilter(firstFilter: GraphFilter, secondFilter: GraphFilter, logicFilter: (Array[Edge], Array[Edge]) => Array[Edge] = (a1, a2) => a1.intersect(a2)): Graph => (Graph, Array[Edge]) = { graph: Graph =>
    val (_, e1) = firstFilter(graph)
    val (_, e2) = secondFilter(graph)
    (graph, logicFilter(e1, e2))
  }

  case class OnlyTaggedWith(tagId: NodeId) extends UserViewGraphTransformation {
    def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter = { graph: Graph =>
      pageId.fold((graph, graph.edges)) { _ =>
        val tagIdx = graph.idToIdx(tagId)
        val newEdges = graph.edges.filter {
          case e: Edge.Child if InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task)(graph.nodesById(e.childId).role) =>
            if(graph.tagParentsIdx.contains(graph.idToIdx(e.childId))(tagIdx)) true else false
          case _                                                                                                                   => true
        }
        (graph, newEdges)
      }
    }
  }

  case object InDeletedGracePeriodParents extends UserViewGraphTransformation {
    def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter = { graph: Graph =>
      pageId.fold((graph, graph.edges)) { pid =>
        val newEdges = graph.edges.filter {
          case e: Edge.Child if e.parentId == pid => graph.isInDeletedGracePeriod(e.childId, pid)
          case _                                     => true
        }
        (graph, newEdges)
      }
    }
  }

  case object OnlyDeletedParents extends UserViewGraphTransformation {
    def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter = { graph: Graph =>
      pageId.fold((graph, graph.edges)) { pid =>
        val pageIdx = graph.idToIdx(pid)
        val newEdges = graph.edges.filter {
          case e: Edge.Child if e.parentId == pid => graph.isDeletedNow(e.childId, pid) || graph.isInDeletedGracePeriod(e.childId, pid)
          case _                                     => true
        }
        (graph, newEdges)
      }
    }
  }

  case object NoDeletedParents extends UserViewGraphTransformation {
    def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter = { graph: Graph =>
      pageId.fold((graph, graph.edges)) { pid =>
        val descendants = graph.descendants(pid)
        val newEdges = graph.edges.filter {
          case e: Edge.Child if descendants.contains(e.childId) => !graph.isDeletedNow(e.childId, pid)
          case _                                  => true
        }
        (graph, newEdges)
      }
    }
  }

  case object NoDeletedButGracedParents extends UserViewGraphTransformation {
    def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter = { graph: Graph =>
      pageId.fold((graph, graph.edges)) { pid =>
        val newEdges = graph.edges.filter {
          case e: Edge.Child if e.parentId == pid => !graph.isDeletedNow(e.childId, pid) || graph.isInDeletedGracePeriod(e.childId, pid)
          case _                                     => true
        }
        (graph, newEdges)
      }
    }
  }

  case object AutomatedHideTemplates extends UserViewGraphTransformation {
    def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter = { graph: Graph =>
      val templateNodeIds: Set[NodeId] = graph.edges.collect { case e: Edge.Automated => e.templateNodeId }(breakOut)
      val newEdges = graph.edges.filter {
        case e: Edge.Child if templateNodeIds.contains(e.childId) => false
        case _              => true
      }
      (graph, newEdges)
    }
  }

  case object OnlyAssignedTo extends UserViewGraphTransformation {
    def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter = { graph: Graph =>
      val assignedNodeIds = graph.edges.collect {
        case e: Edge.Assigned if e.userId == userId => e.nodeId
      }
      val newEdges = graph.edges.filter {
        case e: Edge.Child if graph.nodesById(e.childId).role == NodeRole.Task => assignedNodeIds.contains(e.childId)
        case _                                                                    => true
      }
      (graph, newEdges)
    }
  }

  case object OnlyNotAssigned extends UserViewGraphTransformation {
    def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter = { graph: Graph =>
      val assignedNodeIds = graph.edges.collect {
        case e: Edge.Assigned => e.nodeId
      }
      val newEdges = graph.edges.filterNot {
        case e: Edge.Child => assignedNodeIds.contains(e.childId)
        case _             => false
      }
      (graph, newEdges)
    }
  }

  case object Identity extends UserViewGraphTransformation {
    def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter = { graph: Graph =>
      (graph, graph.edges)
    }
  }
}
