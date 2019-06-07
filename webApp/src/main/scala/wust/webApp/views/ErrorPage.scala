package wust.webApp.views

import fontAwesome.freeSolid
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp.views.Components._

object ErrorPage {
  def apply(errorMessage: Option[String] = None) = div(

    // background animation: https://codepen.io/chris22smith/pen/RZogMa
    div(cls := "error-animation-bg"),
    div(cls := "error-animation-bg error-animation-bg2"),
    div(cls := "error-animation-bg error-animation-bg3"),

    div(
      cls := "error-animation-content",
      h1(replaceEmojiUnified("🤕"), " Oops, an error occurred!"),
      p(VDomModifier(errorMessage.getOrElse("Something went wrong."))),
      button(cls := "ui button positive", margin := "10px 0px", freeSolid.faAmbulance, " Reload Page", onClick.foreach { dom.window.location.reload() }),
      p("If the problem persists, please contact us at ", Components.woostTeamEmailLink)
    ),
  )
}
