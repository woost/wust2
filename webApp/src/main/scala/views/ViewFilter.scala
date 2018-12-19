package wust.webApp.views

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

//  val allTransformations: List[GraphTransformation] =

  def render(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    val onlyDeletedParents = Parents.OnlyDeletedParents(state.page.now.parentId.get).renderReplaceFilter(state)
    val withoutDeletedParents = Parents.WithoutDeletedParents(state.page.now.parentId.get).renderReplaceFilter(state)
    val myAssignments = Assignments.OnlyAssignedTo(state.user.now.id).renderReplaceFilter(state)
    val notAssigned = Assignments.OnlyNotAssigned.renderReplaceFilter(state)
    val noFilters = Identity.renderReplaceFilter(state)

    val filterItems: List[VDomModifier] = List(withoutDeletedParents, onlyDeletedParents, myAssignments, notAssigned, noFilters)

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
  val icon: VDomModifier
  val description: String

  def renderStackFilter(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    render(state, state.graphTransformation.now :+ transform)
  }

  def renderReplaceFilter(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    render(state, Seq(transform))
  }

  private def render(state: GlobalState, graphTransform: Seq[Graph => Graph]) = {
    div(
      cls := "item",
      Elements.icon(icon)(marginRight := "5px"),
      span(cls := "text", description, cursor.pointer),
      onClick.mapTo(graphTransform) --> state.graphTransformation
    )
  }
}

case object Identity extends ViewTransformation {
  val icon = Icons.noFilter
  val description = "Reset ALL filters"
  val transform = identity[Graph]
}

object Parents {

  case class WithoutDeletedParents(pageId: NodeId) extends ViewTransformation {
    val icon = Icons.undelete
    val description = "Do not show deleted items"

    val transform = (graph: Graph) => {
      val newEdges = graph.edges.filterNot {
        case e: Edge.Parent => e.targetId == pageId && graph.isDeletedNow(e.sourceId, Seq(pageId))
        case _              => false
      }
      graph.copy(edges = newEdges)
    }
  }

  case class OnlyDeletedParents(pageId: NodeId) extends ViewTransformation {
    val icon  = Icons.delete
    val description = "Show only deleted items"

    val transform = (graph: Graph) => {
      val newEdges = graph.edges.filter {
        case e: Edge.Parent => e.targetId == pageId && graph.isDeletedNow(e.sourceId, Seq(pageId))
        case _ => true
      }
      graph.copy(edges = newEdges)
    }
  }

}

object Assignments {

  case class OnlyAssignedTo(userId: UserId) extends ViewTransformation  {
    val icon = Icons.task
    val description = s"Show items assigned to: Me"

    val transform = (graph: Graph) => {
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

  case object OnlyNotAssigned extends ViewTransformation {
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

