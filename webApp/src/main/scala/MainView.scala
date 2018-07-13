package wust.webApp

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webApp.outwatchHelpers._
import wust.util._

object MainView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      cls := "flex mainview",
      Topbar(state)(ctx)(width := "100%", flexGrow := 0, flexShrink := 0),
      div(
        cls := "flex growFull",
        Sidebar(state)(ctx),
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
            cls := "flex growFull",
            flexDirection.column,
            Rx {
              state
                .view()
                .isContent
                .ifTrueOption(
                  BreadCrumbs(state)(ctx)
                )
            },
            state.view.map(_.apply(state)(ctx)(cls := "growFull", flexGrow := 1))
          )
        )
      )
    )
  }
}
