package wust.webApp.views

import fomanticui.SidebarOptions
import jquery.JQuerySelection
import monix.reactive.Observable
import monix.reactive.subjects.BehaviorSubject
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx.Var.Assignment
import rx.{Ctx, Rx, Var}
import wust.css.ZIndex
import wust.graph.{Edge, Graph}
import wust.ids.{NodeId, UserId}
import wust.util.macros.SubObjects
import wust.webApp.Icons
import wust.webApp.state.GlobalState
import wust.webApp.outwatchHelpers._
import wust.webApp.views.GraphOperation.GraphTransformation

object ViewFilter {

  private def allTransformations(state: GlobalState)(implicit ctx: Ctx.Owner): List[ViewGraphTransformation] = List(
    Deleted.OnlyDeletedParents(state),
    Deleted.NoDeletedParents(state),
    Assignments.OnlyAssignedTo(state),
    Assignments.OnlyNotAssigned(state),
//    Identity(state),
  )

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
    val activeFilter = Rx { state.graphTransformations().nonEmpty }
    val filterColor = activeFilter.map(active => if(active) VDomModifier( color := "green" ) else VDomModifier.empty)

    div(
      cls := "ui icon top left labeled pointing dropdown",
      Icons.filterDropdown,
      filterColor,
      div(
        cls := "menu",
        div(cls := "header", "Filter", cursor.default),
        filterItems,
        // This does not work because
        div(
          cls := "item",
          Elements.icon(Icons.noFilter)(marginRight := "5px"),
          span(cls := "text", "Reset ALL filters", cursor.pointer),
          onClick.mapTo(Seq.empty[UserViewGraphTransformation]) --> state.graphTransformations
        )
      ),
      UI.tooltip("bottom right") := "Filter items in view",
      zIndex := ZIndex.overlay, // leave zIndex here since otherwise it gets overwritten
      Elements.withoutDefaultPassiveEvents, // revert default passive events, else dropdown is not working
      onDomMount.asJquery.foreach(_.dropdown("hide")), // https://semantic-ui.com/modules/dropdown.html#/usage
    )
  }

  case class Identity(state: GlobalState) extends ViewGraphTransformation {
    val icon = Icons.noFilter
    val description = "Reset ALL filters"
    val transform = GraphOperation.Identity
    val domId = "GraphOperation.Identity"
  }

  object Deleted {

    case class NoDeletedParents(state: GlobalState) extends ViewGraphTransformation {
      val icon = Icons.undelete
      val description = "Do not show deleted items"
      val transform = GraphOperation.NoDeletedParents
      val domId = "GraphOperation.NoDeletedParents"
    }

    case class OnlyDeletedParents(state: GlobalState) extends ViewGraphTransformation {
      val icon  = Icons.delete
      val description = "Show only deleted items"
      val transform = GraphOperation.OnlyDeletedParents
      val domId = "GraphOperation.OnlyDeletedParents"
    }

  }

  object Assignments {

    case class OnlyAssignedTo(state: GlobalState) extends ViewGraphTransformation {
      val icon = Icons.task
      val description = s"Show items assigned to: Me"
      val transform = GraphOperation.OnlyAssignedTo
      val domId = "GraphOperation.OnlyAssignedTo"
    }

    case class OnlyNotAssigned(state: GlobalState) extends ViewGraphTransformation {
      val icon = Icons.task
      val description = "Show items that are not assigned"
      val transform = GraphOperation.OnlyNotAssigned
      val domId = "GraphOperation.OnlyNotAssigned"
    }

  }

}


sealed trait ViewGraphTransformation {
  val state: GlobalState
  val transform: UserViewGraphTransformation
  val icon: VDomModifier
  val description: String
  val domId: String

  def render(implicit ctx: Ctx.Owner) = {

    val activeFilter = (doActivate: Boolean) =>  if(doActivate) {
      state.graphTransformations.now :+ transform
    } else {
      state.graphTransformations.now.filter(_ != transform)
    }

    div(
      cls := "item",
      div(
        cls := "ui toggle checkbox",
        input(tpe := "checkbox",
          id := domId,
          onChange.checked.map(activeFilter) --> state.graphTransformations,
          checked <-- state.graphTransformations.map(_.contains(transform))
        ),
        label(description, `for` := domId),
      ),
      Elements.icon(icon)(marginLeft := "5px"),
    )

  }
}

sealed trait UserViewGraphTransformation { def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation }
object GraphOperation {
  type GraphTransformation = Graph => Graph

  def all: List[UserViewGraphTransformation] = macro SubObjects.list[UserViewGraphTransformation]

  case object OnlyDeletedParents extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = { graph: Graph =>
      pageId.fold(graph) { pid =>
        val newEdges = graph.edges.filter {
          case e: Edge.Parent => e.targetId == pid && graph.isDeletedNow(e.sourceId, pid)
          case _              => true
        }
        graph.copy(edges = newEdges)
      }
    }
  }

  case object NoDeletedParents extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = { graph: Graph =>
      pageId.fold(graph) { pid =>
        val newEdges = graph.edges.filterNot {
          case e: Edge.Parent => e.targetId == pid && graph.isDeletedNow(e.sourceId, pid)
          case _              => false
        }
        graph.copy(edges = newEdges)
      }
    }
  }

  case object OnlyAssignedTo extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = { graph: Graph =>
      val nodeIds = graph.edges.collect {
        case e: Edge.Assigned if e.sourceId == userId => e.targetId
      }
      val newEdges = graph.edges.filter {
        case e: Edge.Parent => nodeIds.contains(e.sourceId) || nodeIds.contains(e.targetId)
        case _              => true
      }
      graph.copy(edges = newEdges)
    }
  }

  case object OnlyNotAssigned extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = { graph: Graph =>
      val nodeIds = graph.edges.collect {
        case e: Edge.Assigned => e.targetId
      }
      val newEdges = graph.edges.filterNot {
        case e: Edge.Parent => nodeIds.contains(e.sourceId) || nodeIds.contains(e.targetId)
        case _              => false
      }
      graph.copy(edges = newEdges)
    }
  }

  case object Identity extends UserViewGraphTransformation {
    def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation = identity[Graph]
  }
}
