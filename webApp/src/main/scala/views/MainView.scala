package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph.Page
import wust.util._
import wust.webApp.DevOnly
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize}

object MainView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      cls := "mainview",
      Styles.flex,
//      DevOnly { DevView(state) },
      Topbar(state)(width := "100%", Styles.flexStatic),
      div(
        Styles.flex,
        Styles.growFull,
        position.relative,
        Sidebar(state),
        backgroundColor <-- state.pageStyle.map(_.bgColor),
        div(
          width := "100%",
          minWidth := "0", // necessary for "overflow-wrap: word-break" in node-content to play nice with nested flexbox (https://jsfiddle.net/j3yc28km/).
          Rx {
            // don't show non-bookmarked border for:
            @inline def noContent = !state.view().isContent
            @inline def isLoading = state.isLoading()
            @inline def isOwnUser = state.page().parentIds == Seq(state.user().id)
            @inline def anyPageParentIsPinned = state.graph().anyAncestorIsPinned(state.page().parentIds)

            (noContent || isLoading || isOwnUser || anyPageParentIsPinned).ifFalse[VDomModifier](
              cls := "non-bookmarked-page-frame"
            )
          },
          div(
            Styles.flex,
            Styles.growFull,
            flexDirection.column,
            Rx {
              val view = state.view()
              view.isContent
                .ifTrueSeq(Seq(
                  (state.screenSize() != ScreenSize.Small).ifTrue[VDomModifier](BreadCrumbs(state)(Styles.flexStatic)),
                  PageHeader(state).apply(Styles.flexStatic)
                ))
            },
            // It is important that the view rendering is in a separate Rx.
            // This avoids rerendering the whole view when only the screen-size changed
            Rx {
              ViewRender(state.view(), state).apply(Styles.growFull, flexGrow := 1)
            },
          )
        )
      )
    )
  }
}
