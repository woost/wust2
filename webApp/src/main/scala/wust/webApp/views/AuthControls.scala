package wust.webApp.views

import wust.facades.googleanalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.outwatchHelpers._
import wust.api.AuthUser
import wust.css.Styles
import wust.graph._
import wust.ids.View
import wust.webApp.Client
import wust.webApp.state._
import wust.ids.Feature

object AuthControls {

  def authStatus(buttonStyleLoggedIn: String, buttonStyleLoggedOut: String)(implicit ctx: Ctx.Owner): Rx[VNode] =
    GlobalState.user.map {
      case user: AuthUser.Assumed  => loginSignupButtons(buttonStyleLoggedOut).apply(Styles.flexStatic)
      case user: AuthUser.Implicit => loginSignupButtons(buttonStyleLoggedOut).apply(Styles.flexStatic)
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
            GlobalState.urlConfig.update(_.focus(View.UserSettings))
            FeatureState.use(Feature.ClickAvatarInAuthStatus)
          },
        ),
        logoutButton(buttonStyleLoggedIn)
      )
    }

  private def loginSignupButtons(buttonStyle: String)(implicit ctx: Ctx.Owner) =
    div(
      button(
        "Signup",
        cls := s"tiny compact ui $buttonStyle button",
        onClick.mapTo(GlobalState.urlConfig.now.focusWithRedirect(View.Signup)) --> GlobalState.urlConfig,
        onClick foreach {
          FeatureState.use(Feature.ClickSignupInAuthStatus)
        },
      ),
      button(
        "Login",
        cls := s"tiny compact ui $buttonStyle button",
        onClick.mapTo(GlobalState.urlConfig.now.focusWithRedirect(View.Login)) --> GlobalState.urlConfig,
        onClick foreach {
          FeatureState.use(Feature.ClickLoginInAuthStatus)
        },
        marginRight := "0",
      )
    )

  private def logoutButton(buttonStyle: String) =
    button(
      "Logout",
      cls := s"tiny compact ui $buttonStyle button",
      onClick foreach {
        Client.auth.logout().foreach { _ =>
          GlobalState.urlConfig.update(_.focus(Page.empty, View.Login))
        }
        FeatureState.use(Feature.ClickLogoutInAuthStatus)
      },
      marginRight := "0",
    )

}
