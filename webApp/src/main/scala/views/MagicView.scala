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
    div(
      keyed,
      Styles.flex,
      flexDirection.column,
      Rx {
        withLoadingAnimation(state) {
          val stats = state.graph().topLevelRoleStats(state.page().parentIds)
          VDomModifier(
            if(stats.contains(NodeRole.Task)) {
              div(
                Styles.growFull,
                Styles.flex,
                flexDirection.column,
                KanbanView.apply(state).apply(Styles.growFull),
                ThreadView.apply(state).apply(Styles.flexStatic, maxHeight := "50%")
              )
            } else {
              ThreadView.apply(state).apply(Styles.growFull)
            }
          )
        }
      }
    )
  }
}
