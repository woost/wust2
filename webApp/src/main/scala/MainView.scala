package wust.webApp

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webApp.outwatchHelpers._
import wust.util._

object MainView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      cls := "flex",
      height := "100%",
      width := "100%",
      flexDirection.column,
      Topbar(state)(ctx)(width := "100%", flexGrow := 0, flexShrink := 0),
      div(
        cls := "flex",
        height := "100%",
        width := "100%",
        Sidebar(state)(ctx)(flexGrow := 0, flexShrink := 0),
        backgroundColor <-- state.pageStyle.bgColor,
        div(
          width := "100%",
          Rx {
            (state.pageIsBookmarked() || !state.view().isContent)
              .ifFalseOption(
                Seq(backgroundColor <-- state.pageStyle.bgColor, cls := "non-bookmarked-page-frame")
              )
          },
          div(
            cls := "flex",
            flexDirection.column,
            width := "100%",
            height := "100%",
            Rx {
              state
                .view()
                .isContent
                .ifTrueOption(
                  BreadCrumbs(state)(ctx)(fontSize := "12px", flexGrow := 0, flexShrink := 0)
                )
            },
            state.view.map(_.apply(state)(ctx)(height := "100%", width := "100%", flexGrow := 1))
          )
        )
      )
    )
  }
}
