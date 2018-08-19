package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webApp.outwatchHelpers._
import wust.util._
import wust.css.Styles
import wust.webApp.state.{GlobalState, ScreenSize}

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
        backgroundColor <-- state.pageStyle.map(_.bgColor),
        div(
          width := "100%",
          overflow.auto, // nobody knows why we need this here, but else overflow in the content does not work
          Rx {
            // don't show non-bookmarked border for:
            val noChannelNodeInGraph = state.graph().channelNodeIds.isEmpty // happens when assumed user clicks on "new channel"
            val bookmarked = state.pageIsBookmarked()
            val viewingChannelNode = state.page().parentIdSet.contains(state.user().channelNodeId)
            val noContent = !state.view().isContent

            (noChannelNodeInGraph || bookmarked || viewingChannelNode || noContent).ifFalseOption(
              cls := "non-bookmarked-page-frame"
            )
          },
          div(
            Styles.flex,
            Styles.growFull,
            flexDirection.column,
            position.relative,
            Rx {
              val view = state.view()
              VDomModifier(
                view
                  .isContent
                  .ifTrueSeq(
                    Seq(
                      (state.screenSize() != ScreenSize.Small).ifTrue[VDomModifier](BreadCrumbs(state)(ctx)(Styles.flexStatic)),
                      PageHeader(state).apply(Styles.flexStatic)
                    )),
                )
            },
            Rx {
              ViewRender(state.view(), state).apply(Styles.growFull, flexGrow := 1)
            },
            SelectedNodes(state).apply(Styles.flexStatic)
          )
        )
      ),
    )
  }
}
