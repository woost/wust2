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

object ViewFilter {

  private def allTransformations(state: GlobalState)(implicit ctx: Ctx.Owner): List[ViewTransformation] = List(
    Parents.OnlyDeletedParents(state),
    Parents.WithoutDeletedParents(state),
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

}

sealed trait GraphTransformation { val transform: Graph => Graph }
sealed trait ViewTransformation extends GraphTransformation {
  val transform: Graph => Graph
  val state: GlobalState
  val icon: VDomModifier
  val description: String

  def renderStackFilter(implicit ctx: Ctx.Owner): VNode = {
    render(state.graphTransformation.now :+ transform)
  }

  def renderReplaceFilter(implicit ctx: Ctx.Owner): VNode = {
    render(Seq(transform))
  }

  private def render(graphTransform: Seq[Graph => Graph]) = {
    a(
      cls := "item",
      Elements.icon(icon)(marginRight := "5px"),
      span(cls := "text", description, cursor.pointer),
      onClick.mapTo(graphTransform) --> state.graphTransformation
    )
  }
}

case class Identity(state: GlobalState) extends ViewTransformation {
  val icon = Icons.noFilter
  val description = "Reset ALL filters"
  val transform = identity[Graph]
}

object Parents {

  case class WithoutDeletedParents(state: GlobalState) extends ViewTransformation {
    val icon = Icons.undelete
    val description = "Do not show deleted items"
    val pageId = (state: GlobalState) => state.page.now.parentId

    val transform = (graph: Graph) => pageId(state).fold(graph){ pid =>
      val newEdges = graph.edges.filterNot {
        case e: Edge.Parent => e.targetId == pid && graph.isDeletedNow(e.sourceId, pid)
        case _              => false
      }
      graph.copy(edges = newEdges)
    }
  }

  case class OnlyDeletedParents(state: GlobalState) extends ViewTransformation {
    val icon  = Icons.delete
    val description = "Show only deleted items"
    val pageId = (state: GlobalState) => state.page.now.parentId

    val transform = (graph: Graph) => pageId(state).fold(graph){ pid =>
      val newEdges = graph.edges.filter {
        case e: Edge.Parent => e.targetId == pid && graph.isDeletedNow (e.sourceId, pid)
        case _ => true
      }
      graph.copy (edges = newEdges)
    }
  }

}

object Assignments {

  case class OnlyAssignedTo(state: GlobalState) extends ViewTransformation  {
    val icon = Icons.task
    val description = s"Show items assigned to: Me"
    val userId = (state: GlobalState) => state.user.now.id

    val transform = (graph: Graph) => {
      val nodeIds = graph.edges.collect {
        case e: Edge.Assigned if e.sourceId == userId(state) => e.targetId
      }
      val newEdges = graph.edges.filter {
        case e: Edge.Parent => nodeIds.contains(e.sourceId) || nodeIds.contains(e.targetId)
        case _              => true
      }
      graph.copy(edges = newEdges)
    }
  }

  case class OnlyNotAssigned(state: GlobalState) extends ViewTransformation {
    val icon = Icons.task
    val description = "Show items that are not assigned"

    val transform = (graph: Graph) => {
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

}

