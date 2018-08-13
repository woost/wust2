package wust.webApp.views

import acyclic.skipped // file is allowed in dependency cycle
import outwatch.dom._
import outwatch.dom.dsl._
import rx.Ctx
import wust.css.Styles
import wust.webApp.{Analytics, GlobalState}
import wust.webApp.MainViewParts.newChannelButton
import wust.webApp.outwatchHelpers._

object NewChannelView extends View {
  override val viewKey = "newchannel"
  override val displayName = "New Channel"
  override def isContent = false

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      justifyContent.spaceAround,
      flexDirection.column,
      alignItems.center,
      newChannelButton(state)(ctx)(padding := "20px", marginBottom := "10%")(
        onClick --> sideEffect { Analytics.sendEvent("view:newchannel", "newchannel") }
      )
    )
  }
}
