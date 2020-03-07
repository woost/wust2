package wust.webApp.views

import outwatch._
import outwatch.dsl._
import colibri.ext.rx._
import rx._
import wust.api.AuthUser
import wust.css.Styles
import wust.graph._
import wust.ids.{ Feature, View }
import wust.webApp.Client
import wust.webApp.state._
import wust.webApp.views.DragComponents.registerDragContainer
import wust.webUtil.outwatchHelpers._
import wust.webUtil.Elements.onClickDefault
import wust.facades.segment.Segment

object AuthControls {

  def authStatusOnLightBackground(showLogin: Boolean = true)(implicit ctx: Ctx.Owner) = authStatus(buttonStyleLoggedIn = "basic", loginButtonStyleLoggedOut = "basic", signupButtonStyleLoggedOut = "pink", showLogin)
  def authStatusOnColoredBackground(showLogin: Boolean = true)(implicit ctx: Ctx.Owner) = authStatus(buttonStyleLoggedIn = "inverted", loginButtonStyleLoggedOut = "inverted", signupButtonStyleLoggedOut = "inverted", showLogin)
  def authStatus(buttonStyleLoggedIn: String, loginButtonStyleLoggedOut: String, signupButtonStyleLoggedOut: String, showLogin: Boolean = true)(implicit ctx: Ctx.Owner): Rx[VNode] =
    GlobalState.user.map {
      case user: AuthUser.Assumed => loginSignupButtons(loginButtonStyleLoggedOut, signupButtonStyleLoggedOut, showLogin).apply(Styles.flexStatic)
      case user: AuthUser.Implicit => div(
        Styles.flex,
        alignItems.center,

        VDomModifier.ifTrue(user.name.nonEmpty)(
          renderAvatarWithUsername(user)
        ),

        loginSignupButtons(loginButtonStyleLoggedOut, signupButtonStyleLoggedOut, showLogin).apply(Styles.flexStatic)
      )
      case user: AuthUser.Real => div(
        Styles.flex,
        alignItems.center,

        renderAvatarWithUsername(user),
        logoutButton (buttonStyleLoggedIn).apply(Styles.flexStatic)
      )
    }

  def renderAvatarWithUsername(user: AuthUser) = {
    div(
      Styles.flex,
      alignItems.center,
      Avatar.user(user.toNode, size = "20px"),
      span(
        cls := "username",
        user.name,
        padding := "0 5px",
        Styles.wordWrap
      ),
      cursor.pointer,
      onClickDefault.foreach {
        GlobalState.urlConfig.update(_.focus(View.UserSettings))
        FeatureState.use(Feature.ClickAvatarInAuthStatus)
        GlobalState.resetGraphTransformation()
      },
      registerDragContainer,
    )
  }

  private def loginSignupButtons(loginButtonStyle: String, signupButtonStyle: String, showLogin: Boolean)(implicit ctx: Ctx.Owner) =
    div(
      VDomModifier.ifTrue(showLogin)(
        button(
          "Login",
          cls := s"tiny compact ui $loginButtonStyle button",
          onClick.useLazy(GlobalState.urlConfig.now.focusWithRedirect(View.Login)) --> GlobalState.urlConfig,
          onClick foreach {
            FeatureState.use(Feature.ClickLoginInAuthStatus)
          },
        ),
      ),
      button(
        "Signup",
        cls := s"tiny compact ui $signupButtonStyle button",
        onClick.useLazy(GlobalState.urlConfig.now.focusWithRedirect(View.Signup)) --> GlobalState.urlConfig,
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
          GlobalState.urlConfig.update(_.focus(Page.empty, View.Login))
        }
        FeatureState.use(Feature.ClickLogoutInAuthStatus)
        Segment.trackSignedOut()
      },
      marginRight := "0",
    )

}
