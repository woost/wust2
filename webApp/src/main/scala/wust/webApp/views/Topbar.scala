package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import wust.css.Styles
import wust.webApp.state._
import wust.webUtil.Ownable
import wust.webUtil.outwatchHelpers._

object Topbar {

  def apply(state: GlobalState): VNode = {
    div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>
      VDomModifier(
        cls := "topbar",
        // wust.webApp.DevOnly(SharedViewElements.createNewButton(state).apply(marginRight := "10px", Styles.flexStatic)),

        AnnouncekitWidget.widget(marginLeft.auto, Styles.flexStatic, color.black),
        FeedbackForm(state)(ctx)(Styles.flexStatic),
        AuthControls.authStatus(state, buttonStyleLoggedIn = "basic", buttonStyleLoggedOut = "primary")
      )
    })
  }

}
