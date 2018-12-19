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

  private def renderStackItem(state: GlobalState, icon: VDomModifier, desc: String, transformation: Graph => Graph)(implicit ctx: Ctx.Owner) = {
    renderStack(state, icon, desc, state.graphTransformation.now :+ transformation)
  }

  private def renderStack(state: GlobalState, icon: VDomModifier, desc: String, transformation: Seq[Graph => Graph])(implicit ctx: Ctx.Owner) = {
    div(
      cls := "item",
      Elements.icon(icon)(marginRight := "5px"),
      span(cls := "text", desc, cursor.pointer),
      onClick.mapTo(transformation) --> state.graphTransformation
    )
  }


  def render(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    val onlyDeletedParents = {
      val transformation = ViewFilter.Parents.onlyDeletedParents(state.page.now.parentId.get)(_)
      renderStackItem(state, Icons.delete, "Show only deleted items", transformation)
    }

    val withoutDeletedParents = {
      val transformation: Graph => Graph = ViewFilter.Parents.withoutDeletedParents(state.page.now.parentId.get)(_)
      renderStackItem(state, Icons.undelete, "Do not show deleted items", transformation)
    }

    val myAssignments = {
      val transformation: Graph => Graph = ViewFilter.Assignments.onlyAssignedTo(state.user.now.id)(_)
      renderStackItem(state, Icons.task, "Show my tasks", transformation)
    }

    val notAssigned = {
      val transformation: Graph => Graph = ViewFilter.Assignments.onlyNotAssigned(_)
      renderStackItem(state, Icons.task, "Show not assigned tasks", transformation)
    }

    val noFilters = {
      val transformation: Seq[Graph => Graph] = Seq.empty[Graph => Graph]
      renderStack(state, Icons.noFilter, "Reset ALL filters", transformation)
    }

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
      zIndex := ZIndex.overlay,                               // leave zIndex here since otherwise it gets overwritten
      Elements.withoutDefaultPassiveEvents,                   // revert default passive events, else dropdown is not working
      onDomMount.asJquery.foreach(_.dropdown("hide")),   // https://semantic-ui.com/modules/dropdown.html#/usage
    )
  }

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
