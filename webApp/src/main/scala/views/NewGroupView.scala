package wust.webApp.views

import acyclic.skipped // file is allowed in dependency cycle
import outwatch.dom._
import outwatch.dom.dsl._
import rx.Ctx
import wust.css.Styles
import wust.webApp.GlobalState
import wust.webApp.MainViewParts.newGroupButton
import wust.webApp.outwatchHelpers._

object NewGroupView extends View {
  override val viewKey = "newgroup"
  override val displayName = "New Group"
  override def isContent = false

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      justifyContent.spaceAround,
      flexDirection.column,
      alignItems.center,
      newGroupButton(state)(ctx)(padding := "20px", marginBottom := "10%")
    )
  }
}
