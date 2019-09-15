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
import wust.webApp.dragdrop.{ DragItem, DragPayload, DragTarget }
import wust.webApp.views.DragComponents.{ drag, dragWithHandle }
import wust.webApp.views.DragComponents.{ registerDragContainer }

object AuthControls {

  def authStatusOnLightBackground(implicit ctx: Ctx.Owner) = authStatus(buttonStyleLoggedIn = "basic", loginButtonStyleLoggedOut = "basic", signupButtonStyleLoggedOut = "pink")
  def authStatusOnColoredBackground(implicit ctx: Ctx.Owner) = authStatus(buttonStyleLoggedIn = "inverted", loginButtonStyleLoggedOut = "inverted", signupButtonStyleLoggedOut = "inverted")
  def authStatus(buttonStyleLoggedIn: String, loginButtonStyleLoggedOut: String, signupButtonStyleLoggedOut: String)(implicit ctx: Ctx.Owner): Rx[VNode] =
    GlobalState.user.map {
      case user: AuthUser.Assumed  => loginSignupButtons(loginButtonStyleLoggedOut, signupButtonStyleLoggedOut).apply(Styles.flexStatic)
      case user: AuthUser.Implicit => loginSignupButtons(loginButtonStyleLoggedOut, signupButtonStyleLoggedOut).apply(Styles.flexStatic)
      case user: AuthUser.Real => div(
        Styles.flex,
        alignItems.center,
        div(
          Styles.flex,
          alignItems.center,
          Avatar.user(user.toNode, size = "20px"),
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
          registerDragContainer,
        ),
        logoutButton(buttonStyleLoggedIn)
      )
    }

  private def loginSignupButtons(loginButtonStyle: String, signupButtonStyle: String)(implicit ctx: Ctx.Owner) =
    div(
      button(
        "Login",
        cls := s"tiny compact ui $loginButtonStyle button",
        onClick.useLazy(GlobalState.urlConfig.now.focus(View.Login)) --> GlobalState.urlConfig,
        onClick foreach {
          FeatureState.use(Feature.ClickLoginInAuthStatus)
        },
      ),
      button(
        "Signup",
        cls := s"tiny compact ui $signupButtonStyle button",
        onClick.useLazy(GlobalState.urlConfig.now.focus(View.Signup)) --> GlobalState.urlConfig,
        onClick foreach {
          FeatureState.use(Feature.ClickSignupInAuthStatus)
        },
        marginRight := "0",
      ),
    )

  private def logoutButton(buttonStyle: String) =
    button(
      "Logout",
      cls := s"tiny compact ui $buttonStyle button",
      onClick foreach {
        Client.auth.logout().foreach { _ =>
          GlobalState.urlConfig.update(_.focusOverride(View.Login))
        }
        FeatureState.use(Feature.ClickLogoutInAuthStatus)
      },
      marginRight := "0",
    )

}
