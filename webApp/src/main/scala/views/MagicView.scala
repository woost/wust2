package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.util._

object MagicView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val stats = Rx { state.graph().topLevelRoleStats(state.page().parentId) }
    val statsContainsTask = Rx { stats() contains NodeRole.Task }
    div(
      keyed,
      Styles.flex,
      flexDirection.column,
      Rx {
        if(statsContainsTask()) {
          div(
            Styles.growFull,
            Styles.flex,
            flexDirection.column,
            KanbanView.apply(state).apply(Styles.growFull),
            ChatView.apply(state).apply(Styles.flexStatic, maxHeight := "50%")
          )
        } else {
          ChatView.apply(state).apply(Styles.growFull)
        }
      }
    )
  }
}
