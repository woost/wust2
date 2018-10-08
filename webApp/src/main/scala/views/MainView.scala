package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph.Page
import wust.util._
import wust.webApp.outwatchHelpers._
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
            val isNewChannelPage = state.page().isInstanceOf[Page.NewChannel]
            val bookmarked = state.pageIsBookmarked()
            val noContent = !state.view().isContent
            val isOwnUser = state.page().parentIds == Seq(state.user().id)

            (isNewChannelPage || bookmarked || noContent || isOwnUser).ifFalseOption(
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
              view.isContent
                .ifTrueSeq(Seq(
                  (state.screenSize() != ScreenSize.Small).ifTrue[VDomModifier](BreadCrumbs(state)(ctx)(Styles.flexStatic)),
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
      ),
    )
  }
}
