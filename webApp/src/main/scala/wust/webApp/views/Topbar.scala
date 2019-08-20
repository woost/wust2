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
        FeatureExplorer(marginLeft.auto, Styles.flexStatic),
        AnnouncekitWidget.widget.apply(Styles.flexStatic, color.black),
        FeedbackForm(ctx)(Styles.flexStatic),
        AuthControls.authStatus(buttonStyleLoggedIn = "basic", loginButtonStyleLoggedOut = "pink basic", signupButtonStyleLoggedOut = "pink")
      )
    })
  }

}
