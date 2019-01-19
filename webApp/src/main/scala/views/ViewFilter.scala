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
import wust.webApp.views.GraphOperation.GraphTransformation

import scala.collection.breakOut

object ViewFilter {

  private def allTransformations(state: GlobalState)(implicit ctx: Ctx.Owner): List[ViewGraphTransformation] = List(
    ViewGraphTransformation.Deleted.inGracePeriod(state),
    ViewGraphTransformation.Deleted.onlyDeleted(state),
    ViewGraphTransformation.Deleted.noDeleted(state),
    ViewGraphTransformation.Deleted.noDeletedButGraced(state),
    ViewGraphTransformation.Assignments.onlyAssignedTo(state),
    ViewGraphTransformation.Assignments.onlyNotAssigned(state),
    ViewGraphTransformation.Automated.hideTemplates(state),
    //    Identity(state),
  )

  // TODO sidebar
//  def renderSidebar(state: GlobalState, sidebarContext: ValueObservable[JQuerySelection], sidebarOpenHandler: ValueObservable[String])(implicit ctx: Ctx.Owner): VNode = {
//
//    val filterItems: List[VDomModifier] = allTransformations(state).map(_.render)
//
//    div(
//      cls := "ui right vertical inverted labeled icon menu sidebar visible",
//      //      zIndex := ZIndex.overlay,
//      filterItems,
//      onDomMount.asJquery.transform(_.combineLatest(sidebarContext.observable)).foreach({ case (elem, side) =>
//        elem
//          .sidebar(new SidebarOptions {
//            transition = "overlay"
//            context = side
//          })
//        //          .sidebar("setting", "transition", "overlay")
//      }: Function[(JQuerySelection, JQuerySelection), Unit])
//    )
//  }

  def renderMenu(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    val filterItems: List[VDomModifier] = allTransformations(state).map(_.render)
    val filterColor = state.isFilterActive.map(active => if(active) VDomModifier( color := "green" ) else VDomModifier.empty)

    div(
      cls := "item",
      Elements.icon(Icons.filter)(marginRight := "5px"),
      filterColor,
      span(cls := "text", "Filter", cursor.default),
      div(
        cls := "menu",
        filterItems,
        // This does not work because
        div(
          cls := "item",
          Elements.icon(Icons.noFilter)(marginRight := "5px"),
          span(cls := "text", "Reset ALL filters", cursor.pointer),
          onClick(Seq.empty[UserViewGraphTransformation]) --> state.graphTransformations,
          onClick foreach { Analytics.sendEvent("filter", "reset") },
        )
      ),
    )
  }

  def addLabeledFilterCheckbox(state: GlobalState, filterName: String, description: VDomModifier, transform: UserViewGraphTransformation)(implicit ctx: Ctx.Owner): VNode = {
    val domId = scala.util.Random.nextString(8)
    val checkbox = addFilterCheckbox(state, filterName, transform)

    div(
      cls := "ui checkbox",
      checkbox(id := domId),
      label(
        `for` := domId,
        description,
      )
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
){

  def render(implicit ctx: Ctx.Owner): BasicVNode = {
    div(
      cls := "item",
      ViewFilter.addLabeledFilterCheckbox(state, transform.toString, description, transform).apply(cls := "toggle"),
      Elements.icon(icon)(marginLeft := "5px"),
    )
  }
}

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
      description = "Do not show deleted items",
      transform = GraphOperation.NoDeletedParents,
    )
    def noDeletedButGraced(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon = Icons.undelete,
      description = "Do not show older deleted items",
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
      description = "Show items that are not assigned",
      transform = GraphOperation.OnlyNotAssigned,
    )
  }

}

sealed trait UserViewGraphTransformation {
  def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation
}
object GraphOperation {
  type GraphTransformation = Graph => Graph

  case class OnlyTaggedWith(tagId: NodeId) extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = { graph: Graph =>
      pageId.fold(graph) { _ =>
        val tagIdx = graph.idToIdx(tagId)
        val newEdges = graph.edges.filter {
          case e: Edge.Parent if InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task)(graph.nodesById(e.sourceId).role) =>
            if(graph.tagParentsIdx.contains(graph.idToIdx(e.sourceId))(tagIdx)) true else false
          case _              => true
        }
        graph.copy(edges = newEdges)
      }
    }
  }

  case object InDeletedGracePeriodParents extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = { graph: Graph =>
      pageId.fold(graph) { pid =>
        val newEdges = graph.edges.filter {
          case e: Edge.Parent if e.targetId == pid => graph.isInDeletedGracePeriod(e.sourceId, pid)
          case _              => true
        }
        graph.copy(edges = newEdges)
      }
    }
  }

  case object OnlyDeletedParents extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = { graph: Graph =>
      pageId.fold(graph) { pid =>
        val pageIdx = graph.idToIdx(pid)
        val newEdges = graph.edges.filter {
          case e: Edge.Parent if e.targetId == pid => graph.isDeletedNow(e.sourceId, pid) || graph.isInDeletedGracePeriod(e.sourceId, pid)
          case _              => true
        }
        graph.copy(edges = newEdges)
      }
    }
  }

  case object NoDeletedParents extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = { graph: Graph =>
      pageId.fold(graph) { pid =>
        val newEdges = graph.edges.filter {
          case e: Edge.Parent if e.targetId == pid => !graph.isDeletedNow(e.sourceId, pid)
          case _              => true
        }
        graph.copy(edges = newEdges)
      }
    }
  }

  case object NoDeletedButGracedParents extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = { graph: Graph =>
      pageId.fold(graph) { pid =>
        val newEdges = graph.edges.filter {
          case e: Edge.Parent if e.targetId == pid => !graph.isDeletedNow(e.sourceId, pid) || graph.isInDeletedGracePeriod(e.sourceId, pid)
          case _              => true
        }
        graph.copy(edges = newEdges)
      }
    }
  }

  case object AutomatedHideTemplates extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = { graph: Graph =>
      val templateNodeIds: Set[NodeId] = graph.edges.collect { case e: Edge.Automated => e.templateNodeId }(breakOut)
      val newEdges = graph.edges.filter {
        case e: Edge.Parent if templateNodeIds.contains(e.sourceId) => false
        case _              => true
      }
      graph.copy(edges = newEdges)
    }
  }

  case object OnlyAssignedTo extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = { graph: Graph =>
      val assignedNodeIds = graph.edges.collect {
        case e: Edge.Assigned if e.sourceId == userId => e.targetId
      }
      val newEdges = graph.edges.filter {
        case e: Edge.Parent if graph.nodesById(e.sourceId).role == NodeRole.Task => assignedNodeIds.contains(e.sourceId)
        case _              => true
      }
      graph.copy(edges = newEdges)
    }
  }

  case object OnlyNotAssigned extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = { graph: Graph =>
      val assignedNodeIds = graph.edges.collect {
        case e: Edge.Assigned => e.targetId
      }
      val newEdges = graph.edges.filterNot {
        case e: Edge.Parent => assignedNodeIds.contains(e.sourceId)
        case _              => false
      }
      graph.copy(edges = newEdges)
    }
  }

  case object Identity extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = identity[Graph]
  }
}
