package wust.webApp

import org.scalajs.dom.experimental.permissions.PermissionState
import outwatch.AsVDomModifier
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webApp.fontAwesome.freeSolid._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.View
import Sidebar.sidebar

object MainView {
  import MainViewParts._

  def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    div(
      height := "100%",
      width := "100%",
      display.flex,
      sidebar(state)(owner)(flexGrow := 0, flexShrink := 0),
      backgroundColor <-- state.pageStyle.map(_.bgColor.toHex),
      Rx {
        (if (!state.view().innerViews.forall(View.contentList.toList.contains) || state.page().parentIds.nonEmpty) {
          state.view().apply(state)
        } else {
          newGroupPage(state)
        }).apply(flexGrow := 1)
      }
    )
  }
}
