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
import wust.webApp.views.Components._

object WelcomeView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      flexDirection.column,
      div(
        padding := "10px",
        height := "100%",
        Styles.flex,
        justifyContent.spaceAround,
        alignItems.center,
        div(
          Rx{
            val user = state.user().toNode
            VDomModifier(
              h1(
                "Hello ",
                Avatar(user)(width := "1em", height := "1em", cls := "avatar", marginLeft := "0.2em", marginRight := "0.1em", marginBottom := "-3px"),
                displayUserName(user.data),
                "!"
              ),
              div(
                maxWidth := "80ex",
                marginBottom := "50px",
                user.data.isImplicit.ifTrue[VDomModifier](p("You can use Woost without registration. Everything you create is private. If you want to access your data from another device, ", a(href := "#", "create an account",
                  onClick.preventDefault(state.viewConfig.now.showViewWithRedirect(View.Signup)) --> state.viewConfig,
                  onClick.preventDefault foreach { Analytics.sendEvent("topbar", "signup") },
                ),"."))
              )
            )
          },
          marginBottom := "10%",
          textAlign.center,
          newChannelButton(state, label = "+ New Chat", view = View.Chat).apply(padding := "20px", margin := "20px 40px")(
            onClick foreach { Analytics.sendEvent("view:newchannel", "newchat") }
          ),
          newChannelButton(state, label = "+ New List", view = View.Tasks).apply(padding := "20px", margin := "20px 40px")(
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
