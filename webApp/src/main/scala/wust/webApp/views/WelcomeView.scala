package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.facades.googleanalytics.Analytics
import wust.ids.View
import wust.util._
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.SharedViewElements._
import wust.webUtil.outwatchHelpers._
import NewProjectPrompt._

object WelcomeView {

  def apply(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      flexDirection.column,
      div(
        padding := "10px",
        height := "100%",
        Styles.flex,
        justifyContent.spaceAround,
        overflow.auto,
        div(
          Rx{
            val user = GlobalState.user().toNode
            VDomModifier(
              h1(
                "Hello ",
                Avatar(user)(width := "1em", height := "1em", cls := "avatar", marginLeft := "0.2em", marginRight := "0.1em", marginBottom := "-3px"),
                displayUserName(user.data),
                "!"
              ),
              div(
                cls := "ui segment",
                maxWidth := "80ex",
                marginBottom := "50px",
                h3("Welcome to Woost!"),
                p("If you are new to Woost, start by creating a Project."),
                p("In a ", b("Project"), " you can invite other people to collaborate. You can also add different tools, like a ", b("Checklist"), ", a ", b("Kanban Board"), " or a ", b("Chat."))
              )
            )
          },
          marginBottom := "10%",
          textAlign.center,
          newProjectButton().apply(
            cls := "primary",
            padding := "20px",
            margin := "0px 40px",
            onClick foreach {
              Analytics.sendEvent("view:welcome", "newproject")
            },
          ),
          Rx{
            val user = GlobalState.user().toNode
            user.data.isImplicit.ifTrue[VDomModifier](
              div(
                cls := "ui segment",
                maxWidth := "80ex",
                marginTop := "50px",
                marginBottom := "50px",
                p("You can use Woost without registration."), p("Everything you create is private (unless you share it). Whenever you want to access your data from another device, just ", a(href := "#", "create an account",
                  onClick.preventDefault(GlobalState.urlConfig.now.focusWithRedirect(View.Signup)) --> GlobalState.urlConfig,
                  onClick.preventDefault foreach { Analytics.sendEvent("topbar", "signup") }), ".")
              )
            )
          }
        )
      ),
      Rx {
        (GlobalState.screenSize() == ScreenSize.Small).ifTrue[VDomModifier](
          div(
            padding := "15px",
            div(
              Styles.flex,
              alignItems.center,
              justifyContent.spaceAround,
              AuthControls.authStatus( buttonStyleLoggedIn = "basic", buttonStyleLoggedOut = "primary")
            )
          )
        )
      }
    )
  }
}
