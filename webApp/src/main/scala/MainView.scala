package wust.webApp

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webApp.Sidebar.{sidebar, topbar}
import wust.webApp.MainViewParts.upButton
import wust.webApp.outwatchHelpers._

object MainView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      height := "100%",
      width := "100%",
      display.flex,
      flexDirection.column,
      topbar(state)(ctx)(width := "100%", flexGrow := 0, flexShrink := 0),
      div(
        display.flex,
        height := "100%",
        width := "100%",
        sidebar(state)(ctx)(flexGrow := 0, flexShrink := 0),
        backgroundColor <-- state.pageStyle.map(_.bgColor.toHex),
        div(
          display.flex,
          flexDirection.column,
          upButton(state)(ctx)(fontSize := "12px"),
          state.view.map(_.apply(state)(ctx)(height := "100%", width := "100%", flexGrow := 1))
        )
      )
    )
  }
}
