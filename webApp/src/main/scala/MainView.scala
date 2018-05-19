package wust.webApp

import org.scalajs.dom.experimental.permissions.PermissionState
import outwatch.AsVDomModifier
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webApp.fontAwesome.freeSolid._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.View
import Sidebar.{sidebar, topbar}

object MainView {
  import MainViewParts._

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
        Rx {
          (if (!state.view().isContent || state.page().parentIds.nonEmpty) {
            state.view().apply(state)(ctx)(height := "100%", width := "100%")
          } else {
            newGroupPage(state)
          }).apply(flexGrow := 1)
        }
      )
    )
  }
}
