package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.css.{Styles, ZIndex}
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.util._

// Combines linear chat and thread-view into one view with a thread-switch
object TasksView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val kanban = Var(false)
    div(
      Styles.flex,
      flexDirection.column,
      keyed,

      kanbanSwitchBar(kanban),

      Rx{
        if(kanban()) KanbanView(state).apply(
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

  private def kanbanSwitchBar(kanban:Var[Boolean]):VDomModifier = {
    VDomModifier(
      div(
        marginTop := "15px",
        padding := "0px 15px 5px 5px",
        textAlign.right,

        UI.toggle("Kanban") --> kanban,
      ),
    )
  }
}
