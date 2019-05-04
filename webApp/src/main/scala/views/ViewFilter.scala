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
import wust.ids._
import wust.graph.GraphChanges
import wust.util.algorithm
import wust.util.macros.{InlineList, SubObjects}
import wust.webApp.Ownable
import wust.webApp.Icons
import wust.webApp.state.GlobalState
import wust.webApp.outwatchHelpers._
import wust.webApp.views.GraphOperation.{GraphFilter, GraphTransformation}

import scala.collection.breakOut

object ViewFilter {

  private def allTransformations(state: GlobalState)(implicit ctx: Ctx.Owner): List[ViewGraphTransformation] = List(
//    ViewGraphTransformation.Deleted.inGracePeriod(state),
    ViewGraphTransformation.Deleted.onlyDeleted(state),
    ViewGraphTransformation.Deleted.excludeDeleted(state),
//    ViewGraphTransformation.Deleted.noDeletedButGraced(state),
    ViewGraphTransformation.Assignments.onlyAssignedTo(state),
    ViewGraphTransformation.Assignments.onlyNotAssigned(state),
    ViewGraphTransformation.Automated.hideTemplates(state),
    //    Identity(state),
  )

  def moveableWindow(state: GlobalState, position: MoveableElement.Position)(implicit ctx: Ctx.Owner): MoveableElement.Window = {

    val filterTransformations: Seq[ViewGraphTransformation] = allTransformations(state)

    MoveableElement.Window(
      VDomModifier(
        Icons.filter,
        span(marginLeft := "5px", "Filter"),
        state.isFilterActive.map {
          case true => backgroundColor := "green"
          case false => VDomModifier.empty
        }
      ),
      toggle = state.showFilterList,
      initialPosition = position,
      initialWidth = 260,
      initialHeight = 250,
      resizable = false,
      bodyModifier = Ownable { implicit ctx =>
        VDomModifier(
          padding := "5px",
          Rx {
            backgroundColor := state.pageStyle().bgLightColor,
          },

          Components.verticalMenu(
            filterTransformations.map { transformation =>
              Components.MenuItem(
                title = transformation.icon,
                description = transformation.description,
                active = state.graphTransformations.map(_.contains(transformation.transform) ^ transformation.invertedSwitch),
                clickAction = { () =>
                  state.graphTransformations.update { transformations =>
                    if (transformations.contains(transformation.transform)) transformations.filter(_ != transformation.transform)
                    else transformations.filterNot(transformation.disablesTransform.contains) ++ (transformation.enablesTransform :+ transformation.transform)
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
            onClick(state.defaultTransformations) --> state.graphTransformations,
            onClick foreach { Analytics.sendEvent("filter", "reset") },
          )
        )
      }
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

  def addCurrentlyFilteredTags(state: GlobalState, nodeId: NodeId) = {
    val currentTagFilters:Seq[ParentId] = {
      state.graphTransformations.now.collect {
        case GraphOperation.OnlyTaggedWith(tagId) => ParentId(tagId)
      }
    }
    GraphChanges.addToParents(ChildId(nodeId), currentTagFilters)
  }
}


case class ViewGraphTransformation(
  state: GlobalState,
  transform: UserViewGraphTransformation,
  icon: VDomModifier,
  description: String,
  enablesTransform: Seq[UserViewGraphTransformation] = Seq.empty[UserViewGraphTransformation],
  disablesTransform: Seq[UserViewGraphTransformation] = Seq.empty[UserViewGraphTransformation],
  invertedSwitch: Boolean = false, //Filter is active, so enabling it will turn it off
)

object ViewGraphTransformation {
  def identity(state: GlobalState) = ViewGraphTransformation(
    state = state,
    icon = Icons.noFilter,
    description = "Reset ALL filters",
    transform = GraphOperation.Identity,
  )

  object Deleted {
    def onlyInDeletionGracePeriod(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon  = Icons.delete,
      description = "Show soon auto-deleted items",
      transform = GraphOperation.OnlyInDeletionGracePeriodChildren,
    )
    def onlyDeleted(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon = Icons.delete,
      description = "Show only deleted items",
      transform = GraphOperation.OnlyDeletedChildren,
      disablesTransform = List(GraphOperation.ExcludeDeletedChildren)
    )
    def excludeDeleted(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon = Icons.undelete,
      description = "Show deleted items", // Turns this filter off
      transform = GraphOperation.ExcludeDeletedChildren,
      invertedSwitch = true
    )
    def includeDeletionGracePeriod(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon = Icons.undelete,
      description = "Show older deleted items",
      transform = GraphOperation.IncludeInDeletionGracePeriodChildren,
      invertedSwitch = true
    )
  }

  object Automated {
    def hideTemplates(state: GlobalState) = ViewGraphTransformation(
      state = state,
      icon = Icons.automate,
      description = "Show automation templates", // Turns this filter off
      transform = GraphOperation.AutomatedHideTemplates,
      invertedSwitch = true
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
        graph.idToIdxGet(tagId).fold((graph, graph.edges)) { tagIdx =>
          val newEdges = graph.edges.filter {
            case e: Edge.Child =>
              graph.idToIdxGet(e.childId).fold(false) { childIdx =>
                val node = graph.nodes(childIdx)
                if(InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task)(node.role)) {
                  if(graph.tagParentsIdx.contains(childIdx)(tagIdx)) true
                  else {
                    val tagDescendants = graph.descendantsIdx(tagIdx)
                    if(tagDescendants.contains(childIdx)) true
                    else false
                  }
                } else true
              }
            case _                                                                                                                   => true
          }
          (graph, newEdges)
        }
      }
    }
  }

  case object OnlyInDeletionGracePeriodChildren extends UserViewGraphTransformation {
    def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter = { graph: Graph =>
      pageId.fold((graph, graph.edges)) { _ =>
        val newEdges = graph.edges.filter {
          case e: Edge.Child => graph.isInDeletedGracePeriod(e.childId, e.parentId)
          case _                                     => true
        }
        (graph, newEdges)
      }
    }
  }

  case object OnlyDeletedChildren extends UserViewGraphTransformation {
    def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter = { graph: Graph =>
      pageId.fold((graph, graph.edges)) { _ =>
        val newEdges = graph.edges.filter {
          case e: Edge.Child => graph.isDeletedNow(e.childId, e.parentId) || graph.isInDeletedGracePeriod(e.childId, e.parentId)
          case _                                     => true
        }
        (graph, newEdges)
      }
    }
  }

  case object ExcludeDeletedChildren extends UserViewGraphTransformation {
    def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter = { graph: Graph =>
      pageId.fold((graph, graph.edges)) { _ =>
        val newEdges = graph.edges.filter {
          case e: Edge.Child  => !graph.isDeletedNow(e.childId, e.parentId)
          case _                                  => true
        }
        (graph, newEdges)
      }
    }
  }

  case object IncludeInDeletionGracePeriodChildren extends UserViewGraphTransformation {
    def filterWithViewData(pageId: Option[NodeId], userId: UserId): GraphFilter = { graph: Graph =>
      pageId.fold((graph, graph.edges)) { _ =>
        val newEdges = graph.edges.filter {
          case e: Edge.Child => !graph.isDeletedNow(e.childId, e.parentId) || graph.isInDeletedGracePeriod(e.childId, e.parentId)
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
