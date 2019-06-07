package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import webUtil.Ownable
import webUtil.outwatchHelpers._
import wust.css.Styles
import wust.webApp.state._

object Topbar {

  def apply(state: GlobalState): VNode = {
    div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>
      VDomModifier(
        cls := "topbar",
        // wust.webApp.DevOnly(SharedViewElements.createNewButton(state).apply(marginRight := "10px", Styles.flexStatic)),

        FeedbackForm(state)(ctx)(marginLeft.auto, Styles.flexStatic),
        AuthControls.authStatus(state, buttonStyleLoggedIn = "basic", buttonStyleLoggedOut = "primary")
      )
    })
  }


}
