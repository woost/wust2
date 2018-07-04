package wust.webApp

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webApp.Sidebar.{sidebar, topbar}
import wust.webApp.BreadCrumbs
import wust.webApp.outwatchHelpers._

object MainView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      height := "100%",
      width := "100%",
      display.flex,
      minHeight := "0", // fixes overflow:scroll inside flexbox (https://stackoverflow.com/questions/28636832/firefox-overflow-y-not-working-with-nested-flexbox/28639686#28639686)
      minWidth := "0", // fixes full page scrolling when messages are too long
      flexDirection.column,
      topbar(state)(ctx)(width := "100%", flexGrow := 0, flexShrink := 0),
      div(
        display.flex,
        minHeight := "0", // fixes overflow:scroll inside flexbox (https://stackoverflow.com/questions/28636832/firefox-overflow-y-not-working-with-nested-flexbox/28639686#28639686)
        minWidth := "0", // fixes full page scrolling when messages are too long
        height := "100%",
        width := "100%",
        sidebar(state)(ctx)(flexGrow := 0, flexShrink := 0),
        backgroundColor <-- state.pageStyle.map(_.bgColor.toHex),
        div(
          display.flex,
          minHeight := "0", // fixes overflow:scroll inside flexbox (https://stackoverflow.com/questions/28636832/firefox-overflow-y-not-working-with-nested-flexbox/28639686#28639686)
          minWidth := "0", // fixes full page scrolling when messages are too long
          flexDirection.column,
          width := "100%",
          BreadCrumbs(state)(ctx)(fontSize := "12px", flexGrow := 0, flexShrink := 0),
          state.view.map(_.apply(state)(ctx)(height := "100%", width := "100%", flexGrow := 1))
        )
      )
    )
  }
}
