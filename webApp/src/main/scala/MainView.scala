package wust.webApp

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webApp.outwatchHelpers._
import wust.util._
import wust.css.Styles

object MainView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      cls := "mainview",
      Styles.flex,
      Topbar(state)(ctx)(width := "100%", Styles.flexStatic),
      div(
        Styles.flex,
        Styles.growFull,
        Sidebar(state)(ctx),
        backgroundColor <-- state.pageStyle.bgColor,
        div(
          width := "100%",
          Rx {
            val bookmarked = state.pageIsBookmarked()
            val viewingChannelNode = state.page().parentIdSet.contains(state.user().channelNodeId)
            val noContent = !state.view().isContent

            (bookmarked || viewingChannelNode || noContent).ifFalseOption(
              Seq(backgroundColor <-- state.pageStyle.bgColor, cls := "non-bookmarked-page-frame")
            )
          },
          div(
            Styles.flex,
            Styles.growFull,
            flexDirection.column,
            Rx {
              state
                .view()
                .isContent
                .ifTrueSeq(
                  Seq(
                    BreadCrumbs(state)(ctx)(Styles.flexStatic),
                    PageHeader(state)(ctx)(Styles.flexStatic)
                  )
                )
            },
            state.view.map(_.apply(state)(ctx)(Styles.growFull, flexGrow := 1)),
            SelectedNodes(state)
          )
        )
      )
    )
  }
}
