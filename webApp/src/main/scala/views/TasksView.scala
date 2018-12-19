package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.css.{Styles, ZIndex}
import wust.graph.Graph
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.util._

// Combines task list and kanban-view into one view with a kanban-switch
object TasksView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val topLevelStageExists = Rx {
      // TODO: we should not react on graph, but on the result of a getGraph Request.
      val page = state.page()
      val graph = state.graph()
      page.parentId.fold(false) { parentId =>
        val parentIdx = graph.idToIdx(parentId)
        val workspacesIdx = graph.workspacesForParent(parentIdx)
        val doneNodes:Array[Int] = workspacesIdx.flatMap(workspaceIdx => graph.doneNodeForWorkspace(workspaceIdx))
        graph.notDeletedChildrenIdx.exists(parentIdx) { childIdx =>
          val node = graph.nodes(childIdx)
          val res = node.role == NodeRole.Stage && !doneNodes.contains(childIdx)
          res
        }
      }
    }
    
    val kanbanSwitch = Var(false)
    val filterAssigned = Var(false)
    state.page.foreach{ _ => kanbanSwitch() = topLevelStageExists.now }
    state.graph.foreach{ _ => kanbanSwitch() = topLevelStageExists.now }

    div(
      Styles.flex,
      flexDirection.column,
      keyed,

      div(
        Styles.flex,
        flexDirection.row,
        justifyContent.flexEnd,
        filterAssignedBar(filterAssigned),
        kanbanSwitchBar(kanbanSwitch),
      ),

      Rx{
        if(kanbanSwitch()) KanbanView(state, filterAssigned()).apply(
          Styles.growFull,
          flexGrow := 1
        )
        else ListView(state, filterAssigned()).apply(
          Styles.growFull,
          flexGrow := 1
        )
      }
    )
  }

  private def filterAssignedBar(filterAssigned: Var[Boolean]):VDomModifier = {
    VDomModifier(
      div(
        marginTop := "15px",
        padding := "0px 15px 5px 5px",
        textAlign.right,

        UI.tooltip("bottom right") := "Show only tasks that are assigned to me.",
        UI.toggle("Only my tasks", filterAssigned),

        zIndex := ZIndex.overlaySwitch, // like selectednodes, but still below
      ),
    )
  }

  private def kanbanSwitchBar(kanbanSwitch:Var[Boolean]):VDomModifier = {
    VDomModifier(
      div(
        marginTop := "15px",
        padding := "0px 15px 5px 5px",
        textAlign.right,

        UI.tooltip("bottom right") := "Show tasks in a kanban or as list.",
        UI.toggle("Kanban", kanbanSwitch),

        zIndex := ZIndex.overlaySwitch, // like selectednodes, but still below
      ),
    )
  }
}
