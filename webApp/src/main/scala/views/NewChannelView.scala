package wust.webApp.views

import googleAnalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import rx.Ctx
import wust.css.Styles
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.SharedViewElements._

object NewChannelView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      justifyContent.spaceAround,
      flexDirection.column,
      alignItems.center,
      newChannelButton(state)(padding := "20px", marginBottom := "10%")(
        onClick foreach { Analytics.sendEvent("view:newchannel", "newchannel") }
      )
    )
  }
}
