package wust.webApp

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webApp.outwatchHelpers._
import wust.util._
import wust.css.Styles
import wust.webApp.views.Elements._

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
          Rx {
            // don't show non-bookmarked border for:
            val noChannelNodeInGraph = state.graph().channelNodeIds.isEmpty // happens when assumed user clicks on "new group"
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
            Rx {
              val view = state.view() //TODO: fix Rx bug, where you have to assign a value before using it
              VDomModifier(
                view
                  .isContent
                  .ifTrueSeq(
                    Seq(
                      BreadCrumbs(state)(ctx)(Styles.flexStatic),
                      PageHeader(state)(ctx)(Styles.flexStatic)
                    )),

                view.apply(state)(ctx)(Styles.growFull, flexGrow := 1)
              )
            },
            SelectedNodes(state)(ctx)(Styles.flexStatic)
          )
        )
      ),
      registerDraggableContainer(state)
    )
  }
}
