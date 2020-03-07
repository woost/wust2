package wust.webApp.views

import wust.css.{Styles, ZIndex}
import fontAwesome.freeSolid
import org.scalajs.dom
import outwatch._
import outwatch.dsl._
import wust.webApp.views.Components._
import wust.webUtil.outwatchHelpers._
import wust.facades.segment.Segment

object ErrorPage {
  def apply(errorMessage: Option[String] = None) = {
    Segment.page("Error")
    div(

      // background animation: https://codepen.io/chris22smith/pen/RZogMa
      div(cls := "error-animation-bg"),
      div(cls := "error-animation-bg error-animation-bg2"),
      div(cls := "error-animation-bg error-animation-bg3"),

      div(
        cls := "error-animation-content",
        h1(replaceEmojiUnified("🤕"), " Oops, an error occurred!"),
        p(VDomModifier(errorMessage.getOrElse("Something went wrong."))),
        div(
          Styles.flex,
          flexWrap.wrap,
          alignItems.center,
          margin := "10px 0px",

          button(freeSolid.faAmbulance, " Reload Page", cls := "ui button positive", onClick.foreach { dom.window.location.reload() }, Styles.flexStatic, marginTop := "5px"),
          FeedbackForm.supportChatButton(Styles.flexStatic, marginTop := "5px"),
        ),
        p("If the problem persists, please contact us at ", woostEmailLink("support"))
      ),
    )
  }
}
