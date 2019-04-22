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
import wust.webApp.{Client, DevOnly, Ownable, WoostNotification, BrowserDetect}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState, PageStyle, ScreenSize}
import wust.webApp.views.Components._

import scala.concurrent.Future
import scala.util.Success

object MainView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Rx {
        if (state.hasError()) ErrorPage()
        else main(state)
      }
    )
  }


  private def main(state: GlobalState)(implicit ctx: Ctx.Owner): VDomModifier = {

    // a view should never be shrinked to less than 500px
    val viewWidthMod = minWidth := (600 - LeftSidebar.minWidthSidebar) + "px"

    val projectName = Rx {
      state.page().parentId.map(pid => state.graph().nodesById(pid).str)
    }
    val viewIsContent = Rx {
      state.view().isContent
    }

    VDomModifier(
      Styles.flex,
      Styles.growFull,

      div(id := "draggable-mirrors", position.absolute), // draggable mirror is positioned absolute, so that mirrors do not affect the page layout (especially flexbox) and therefore do not produce ill-sized or misplaced mirrors

      div(
        cls := "mainview",
//      DevOnly { DevView(state) },

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

            backgroundColor <-- state.pageStyle.map(_.bgLightColor),

            Rx {
              VDomModifier.ifTrue(viewIsContent())(div(
                VDomModifier.ifTrue(state.pageHasNotDeletedParents())(BreadCrumbs(state)(Styles.flexStatic)),
                PageHeader(state).apply(Styles.flexStatic, viewWidthMod)
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
              viewWidthMod,

              Styles.flex,
              Styles.growFull,

              div(
                Styles.flex,
                Styles.growFull,
                cls := "pusher",
                Rx {
                  val viewConfig = state.viewConfig()

                    ViewRender(state, FocusState.fromGlobal(state, viewConfig), viewConfig.view).apply(
                      Styles.growFull,
                      flexGrow := 1
                    ).prepend(
                    overflow.visible, // we set a default overflow. we cannot just set it from outside, because every view might have a differnt nested area that is scrollable. Example: Chat which has an input at the bottom and the above history is only scrollable.
                  )
                  // we can now assume, that every page parentId is contained in the graph
                },
              ),

              VDomModifier.ifNot(BrowserDetect.isMobile)(
                position.relative, // needed for taglist window
                MoveableElement.withToggleSwitch(
                  ViewFilter.moveableWindow(state, MoveableElement.RightPosition(100, 500)) ::
                  TagList.moveableWindow(state, MoveableElement.RightPosition(100, 400)) ::
                  Nil,
                  enabled = state.urlConfig.map(c => c.pageChange.page.parentId.isDefined && c.view.forall(_.isContent)),
                  resizeEvent = state.rightSidebarNode.toTailObservable.map(_ => ()),
                )
              )
            ),
          ),
        ),
      ),

      position.relative, // needed for expanded right sidebar on mobile
      RightSidebar(state),
    )
  }
}
