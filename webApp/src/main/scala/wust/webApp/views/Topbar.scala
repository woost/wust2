package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import wust.css.Styles
import wust.webApp.state._
import wust.webUtil.Ownable
import wust.webUtil.outwatchHelpers._

object Topbar {

  def apply: VNode = {
    div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>
      VDomModifier(
        cls := "topbar",
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
