package wust.webApp.views

import outwatch._
import outwatch.dsl._
import wust.webUtil.Ownable
import wust.webUtil.outwatchHelpers._

object Topbar {

  def apply: VNode = {
    div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>
      VDomModifier(
        cls := "topbar",

        PaymentView.focusButton,

        AuthControls.authStatus(buttonStyleLoggedIn = "basic", loginButtonStyleLoggedOut = "pink basic", signupButtonStyleLoggedOut = "pink").map(_.apply(
          marginLeft.auto,
          marginTop := "3px",
          marginRight := "3px",
          id := "tutorial-welcome-authcontrols",
        ))
      )
    })
  }

}
