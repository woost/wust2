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
    margin := "5px",
    cls := "ui negative message",
    div(
      cls := "header",
      h3("Oops, an error occurred!"),
    ),
    p(VDomModifier(errorMessage.getOrElse("Something went wrong."))),
    button(cls := "ui button positive", margin := "10px 0px", freeSolid.faAmbulance, " Reload Page", onClick.foreach { dom.window.location.reload() }),
    p("If the problem persists, please contact us at ", Components.woostTeamEmailLink)
  )
}
