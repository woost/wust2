package wust.webApp.views

import googleAnalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.SharedViewElements._
import wust.util._

object NewChannelView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      flexDirection.column,
      div(
        height := "100%",
        Styles.flex,
        justifyContent.spaceAround,
        flexDirection.column,
        alignItems.center,
        newChannelButton(state)(padding := "20px", marginBottom := "10%")(
          onClick foreach { Analytics.sendEvent("view:newchannel", "newchannel") }
        ),
      ),
      Rx {
        (state.screenSize() == ScreenSize.Small).ifTrue[VDomModifier](
          div(
            backgroundColor <-- state.pageStyle.map(_.sidebarBgColor),
            color := "white",
            padding := "15px",
            div(
              Styles.flex,
              alignItems.center,
              justifyContent.spaceAround,
              authStatus(state)
            )
          )
        )
      }
    )
  }
}
