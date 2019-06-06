package wust.webApp.views

import cats.effect.IO
import fontAwesome._
import googleAnalytics.Analytics
import org.scalajs.dom
import org.scalajs.dom.window
import outwatch.dom._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.dom.dsl._
import outwatch.dom.dsl.styles.extra._
import rx._
import wust.api.AuthUser
import wust.css.Styles
import wust.graph._
import wust.ids.View
import wust.util.RichBoolean
import wust.webApp.{Client, Ownable, DevOnly}
import wust.webApp.outwatchHelpers._
import wust.webApp.state._

import scala.scalajs.js

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
