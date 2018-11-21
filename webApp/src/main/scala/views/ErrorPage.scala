package wust.webApp.views

import fontAwesome.freeSolid
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webApp.state.GlobalState
import wust.webApp.outwatchHelpers._

object ErrorPage {
  def apply(errorMessage: Option[String] = None) = div(
    padding := "50px",
    cls := "ui negative message",
    div(
      cls := "header",
      Components.woostIcon,
      b(paddingLeft := "5px", paddingRight := "20px", "Oops, an error occurred!"),
      button(cls := "ui tiny button positive", freeSolid.faAmbulance, " Reload", onClick.foreach { dom.window.location.reload() })
    ),
    p(VDomModifier(errorMessage.getOrElse("Something went wrong"))),
    p("If the problem persists, please contact us at ", Components.woostTeamEmailLink)
  )
}
