package wust.webApp.views

import fomanticui.SidebarOptions
import jquery.JQuerySelection
import monix.reactive.Observable
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx.Ctx
import wust.css.ZIndex
import wust.graph.{Edge, Graph}
import wust.ids.{NodeId, UserId}
import wust.webApp.Icons
import wust.webApp.state.GlobalState
import wust.webApp.outwatchHelpers._
import wust.webApp.views.GraphTransformer.GraphTransformation

object ViewFilter {

  private def allTransformations(state: GlobalState)(implicit ctx: Ctx.Owner): List[ViewGraphTransformation] = List(
    Deleted.OnlyDeletedParents(state),
    Deleted.NoDeletedParents(state),
    Assignments.OnlyAssignedTo(state),
    Assignments.OnlyNotAssigned(state),
    Identity(state),
  )

  def renderSidebar(state: GlobalState, sidebarContext: ValueObservable[JQuerySelection], sidebarOpenHandler: ValueObservable[String])(implicit ctx: Ctx.Owner): VNode = {

    val filterItems: List[VDomModifier] = allTransformations(state).map(_.renderReplaceFilter)

    div(
      cls := "ui right vertical inverted labeled icon menu sidebar visible",
      //      zIndex := ZIndex.overlay,
      filterItems,
      onDomMount.asJquery.transform(_.combineLatest(sidebarContext.observable)).foreach({ case (elem, side) =>
        elem
          .sidebar(new SidebarOptions {
            transition = "overlay"
            context = side
          })
        //          .sidebar("setting", "transition", "overlay")
      }: Function[(JQuerySelection, JQuerySelection), Unit])
    )
  }

  def renderMenu(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    val filterItems: List[VDomModifier] = allTransformations(state).map(_.renderReplaceFilter)

    div(
      cls := "ui icon top left labeled pointing dropdown",
      Icons.filterDropdown,
      div(
        cls := "menu",
        div(cls := "header", "Filter", cursor.default),
        filterItems,
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
    val transform = GraphTransformer.Identity
  }

  object Deleted {

    case class NoDeletedParents(state: GlobalState) extends ViewGraphTransformation {
      val icon = Icons.undelete
      val description = "Do not show deleted items"
      val transform = GraphTransformer.NoDeletedParents
    }

    case class OnlyDeletedParents(state: GlobalState) extends ViewGraphTransformation {
      val icon  = Icons.delete
      val description = "Show only deleted items"
      val transform = GraphTransformer.OnlyDeletedParents
    }

  }

  object Assignments {

    case class OnlyAssignedTo(state: GlobalState) extends ViewGraphTransformation {
      val icon = Icons.task
      val description = s"Show items assigned to: Me"
      val transform = GraphTransformer.OnlyAssignedTo
    }

    case class OnlyNotAssigned(state: GlobalState) extends ViewGraphTransformation {
      val icon = Icons.task
      val description = "Show items that are not assigned"
      val transform = GraphTransformer.OnlyNotAssigned
    }

  }

}



sealed trait ViewGraphTransformation {
  val state: GlobalState
  val transform: UserViewGraphTransformation
  val icon: VDomModifier
  val description: String

  def renderStackFilter(implicit ctx: Ctx.Owner): VNode = {
    render(state.graphTransformations.now :+ transform)
  }

  def renderReplaceFilter(implicit ctx: Ctx.Owner): VNode = {
    render(Seq(transform))
  }

  private def render(graphTransform: Seq[UserViewGraphTransformation]) = {
    a(
      cls := "item",
      Elements.icon(icon)(marginRight := "5px"),
      span(cls := "text", description, cursor.pointer),
      onClick.mapTo(graphTransform) --> state.graphTransformations
    )
  }
}

sealed trait UserViewGraphTransformation { def transformWithViewData(pageId: Option[NodeId], userId: UserId): GraphTransformation }
object GraphTransformer {
  type GraphTransformation = Graph => Graph

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