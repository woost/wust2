package wust.webApp.views

import googleAnalytics.Analytics
import monix.reactive.Observable
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api.AuthUser
import wust.css.{Styles, ZIndex}
import wust.graph.Page
import wust.sdk.NodeColor
import wust.util._
import wust.webApp.{Client, DevOnly, Ownable, WoostNotification}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState, PageStyle, ScreenSize}
import wust.webApp.views.Components._

import scala.concurrent.Future
import scala.util.Success

object MainView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      cls := "pusher",
      Rx {
        if (state.hasError()) ErrorPage()
        else main(state)
      }
    )
  }


  private def main(state: GlobalState)(implicit ctx: Ctx.Owner): VDomModifier = {

    val projectName = Rx {
      state.page().parentId.map(pid => state.graph().nodesById(pid).str)
    }
    val viewIsContent = Rx {
      state.view().isContent
    }

    VDomModifier(
      cls := "mainview",
      Styles.flex,
//      DevOnly { DevView(state) },

      UI.modal(state.uiModalConfig, state.uiModalClose), // one modal instance for the whole page that can be configured via state.modalConfig

      div(id := "draggable-mirrors", position.absolute), // draggable mirror is positioned absolute, so that mirrors do not affect the page layout (especially flexbox) and therefore do not produce ill-sized or misplaced mirrors

      div(
        cls := "topBannerContainer",
        Rx {
          WoostNotification.banner(state, state.permissionState(), projectName())
        }
      ),

      Rx {
        VDomModifier.ifTrue(state.topbarIsVisible())(Topbar(state)(width := "100%", Styles.flexStatic))
      },

      div(
        Styles.flex,
        Styles.growFull,
        position.relative, // needed for mobile expanded sidebar
        LeftSidebar(state),
        div(
          Styles.flex,
          Styles.growFull,
          flexDirection.column,
          overflow.auto,
          position.relative, // important for position absolute of loading animation to have the correct width of its parent element

          Rx {
            VDomModifier.ifTrue(viewIsContent())(div(
              backgroundColor <-- state.pageStyle.map(_.bgColor),
              VDomModifier.ifTrue(state.pageHasParents())(BreadCrumbs(state)(Styles.flexStatic)),
              PageHeader(state).apply(Styles.flexStatic)
            ))
          },

          //TODO: combine with second rx! but it does not work because it would not overlay everthing as it does now.
          Rx {
            if(viewIsContent()) {
              if (state.isLoading()) Components.spaceFillingLoadingAnimation(state).apply(position.absolute, zIndex := ZIndex.loading, backgroundColor := state.pageStyle().bgLightColor)
              else if (state.pageNotFound()) PageNotFoundView(state).apply(position.absolute, zIndex := ZIndex.loading, backgroundColor := state.pageStyle().bgLightColor)
              else VDomModifier.empty
            } else VDomModifier.empty
          },

          // It is important that the view rendering is in a separate Rx.
          // This avoids rerendering the whole view when only the screen-size changed
          div(
            cls := "main-viewrender",
            div(
              Styles.flex,
              Styles.growFull,
              cls := "pusher",
              Rx {
                val viewConfig = state.viewConfig()
                val pageStyle = PageStyle(viewConfig.view, viewConfig.page)
                VDomModifier(
                  backgroundColor := pageStyle.bgLightColor,

                  FocusState.fromGlobal(state, viewConfig).map { focusState =>
                    ViewRender(state, focusState, viewConfig.view).apply(
                      Styles.growFull,
                      flexGrow := 1
                    ).prepend(
                      overflow.visible
                    )
                  }
                )
                // we can now assume, that every page parentId is contained in the graph
              },
            ),

            Styles.flex,
            Styles.growFull,
            position.relative, // needed for mobile expanded sidebar

            SharedViewElements.tagListWindow(state),
            UI.sidebar(state.uiSidebarConfig, state.uiSidebarClose, targetSelector = Some(".main-viewrender")), // one sidebar instance for the whole page that can be configured via state.sidebarConfig
          ),
        ),

        position.relative,
        RightSidebar(state),
      )
    )
  }
}
