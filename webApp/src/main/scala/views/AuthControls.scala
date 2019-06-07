package wust.webApp.views

import googleAnalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import webUtil.outwatchHelpers._
import wust.api.AuthUser
import wust.css.Styles
import wust.graph._
import wust.ids.View
import wust.webApp.Client
import wust.webApp.state._

object AuthControls {

  def authStatus(state: GlobalState, buttonStyleLoggedIn: String, buttonStyleLoggedOut: String)(implicit ctx: Ctx.Owner): Rx[VNode] =
    state.user.map {
      case user: AuthUser.Assumed  => loginSignupButtons(state, buttonStyleLoggedOut).apply(Styles.flexStatic)
      case user: AuthUser.Implicit => loginSignupButtons(state, buttonStyleLoggedOut).apply(Styles.flexStatic)
      case user: AuthUser.Real => div(
        Styles.flex,
        alignItems.center,
        div(
          Styles.flex,
          alignItems.center,
          Avatar.user(user.id)(height := "20px", cls := "avatar"),
          span(
            user.name,
            padding := "0 5px",
            Styles.wordWrap
          ),
          cursor.pointer,
          onClick foreach {
            state.urlConfig.update(_.focus(View.UserSettings))
            Analytics.sendEvent("authstatus", "avatar")
          },
        ),
        logoutButton(state, buttonStyleLoggedIn)
      )
    }

  private def loginSignupButtons(state: GlobalState, buttonStyle: String)(implicit ctx: Ctx.Owner) =
    div(
      button(
        "Signup",
        cls := s"tiny compact ui $buttonStyle button",
        onClick.mapTo(state.urlConfig.now.focusWithRedirect(View.Signup)) --> state.urlConfig,
        onClick foreach {
          Analytics.sendEvent("topbar", "signup")
        },
      ),
      button(
        "Login",
        cls := s"tiny compact ui $buttonStyle button",
        onClick.mapTo(state.urlConfig.now.focusWithRedirect(View.Login)) --> state.urlConfig,
        onClick foreach {
          Analytics.sendEvent("topbar", "login")
        },
        marginRight := "0",
      )
    )

  private def logoutButton(state: GlobalState, buttonStyle: String) =
    button(
      "Logout",
      cls := s"tiny compact ui $buttonStyle button",
      onClick foreach {
        Client.auth.logout().foreach { _ =>
          state.urlConfig.update(_.focus(Page.empty, View.Login))
        }
        Analytics.sendEvent("topbar", "logout")
      },
      marginRight := "0",
    )

}
