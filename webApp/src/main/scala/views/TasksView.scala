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
      val page = state.page()
      val graph = state.graph()
      page.parentId.fold(false) { parentId =>
        val parentIdx = graph.idToIdx(parentId)
        graph.childrenIdx.exists(parentIdx) { childIdx =>
          val node = graph.nodes(childIdx)
          node.role == NodeRole.Stage && node.str.toLowerCase != Graph.doneTextLower
        }
      }
    }
    val kanbanSwitch = Var(false)
    topLevelStageExists.foreach{kanbanSwitch() = _}

    div(
      Styles.flex,
      flexDirection.column,
      keyed,

      kanbanSwitchBar(kanbanSwitch),

      Rx{
        if(kanbanSwitch()) KanbanView(state).apply(
          Styles.growFull,
          flexGrow := 1
        )
        else ListView(state).apply(
          Styles.growFull,
          flexGrow := 1
        )
      }
    )
  }

  private def kanbanSwitchBar(kanbanSwitch:Var[Boolean]):VDomModifier = {
    VDomModifier(
      div(
        marginTop := "15px",
        padding := "0px 15px 5px 5px",
        textAlign.right,

        UI.toggle("Kanban", kanbanSwitch),
      ),
    )
  }
}
