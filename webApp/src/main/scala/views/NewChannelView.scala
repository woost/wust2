package wust.webApp.views

import googleAnalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.ids.NodeRole
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize, View}
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
        alignItems.center,
        div(
          marginBottom := "10%",
          textAlign.center,
          newChannelButton(state, label = "+ New Chat", view = View.Chat).apply(padding := "20px", margin := "20px 40px")(
            onClick foreach { Analytics.sendEvent("view:newchannel", "newchat") }
          ),
          newChannelButton(state, label = "+ New List", view = View.List).apply(padding := "20px", margin := "20px 40px")(
            onClick foreach { Analytics.sendEvent("view:newchannel", "newlist") }
          ),
          newChannelButton(state, label = "+ New Kanban", view = View.Kanban).apply(padding := "20px", margin := "20px 40px")(
            onClick foreach { Analytics.sendEvent("view:newchannel", "newkanban") }
          ),
        )
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
