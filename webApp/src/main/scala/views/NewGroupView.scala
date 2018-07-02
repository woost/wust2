package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx.Ctx
import wust.webApp.GlobalState
import wust.webApp.MainViewParts.newGroupButton

object NewGroupView extends View {
  override val key = "newgroup"
  override val displayName = "New Group"

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(
      display.flex,
      justifyContent.spaceAround,
      flexDirection.column,
      alignItems.center,
      newGroupButton(state)(ctx)(padding := "20px", marginBottom := "10%")
    )
  }
}
