package wust.webApp.views

import fontAwesome.freeSolid
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.BrowserDetect
import wust.webUtil.outwatchHelpers._
import wust.css.{Styles, ZIndex}
import wust.sdk.Colors
import wust.webApp.WoostNotification
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._

object MainView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Styles.growFull,
      Rx {
        if (state.hasError()) ErrorPage()
        else main(state)
      }
    )
  }

  private def main(state: GlobalState)(implicit ctx: Ctx.Owner): VDomModifier = {
    VDomModifier(
      div(
        Styles.flex,
        Styles.growFull,

        position.relative, // needed for mobile expanded sidebars
        LeftSidebar(state),

        div(
          Styles.flex,
          Styles.growFull,
          flexDirection.column,

          //      DevOnly { DevView(state) },
          topBannerContainer(state),
          content(state),
        ),

        RightSidebar(state, ViewRender),
      )
    )
  }

  def topBannerContainer(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val projectName = Rx {
      state.page().parentId.map(pid => state.graph().nodesByIdOrThrow(pid).str)
    }

    div(
      cls := "topBannerContainer",
      Rx {
        WoostNotification.banner(state, state.permissionState(), projectName())
      }
    )
  }

  def content(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val viewIsContent = Rx {
      state.view().isContent
    }

    // a view should never be shrinked to less than 300px-45px collapsed sidebar
    val viewWidthMod = minWidth := s"${300 - LeftSidebar.minWidthSidebar}px"

    div(
      Styles.flex,
      Styles.growFull,

      flexDirection.column,
      overflow.auto,
      position.relative, // important for position absolute of loading animation to have the correct width of its parent element

      backgroundColor := Colors.contentBg,

      Rx {
        if(viewIsContent())
          PageHeader(state, ViewRender).apply(Styles.flexStatic, viewWidthMod)
        else {
          VDomModifier.ifTrue(state.screenSize() != ScreenSize.Small)(
            Topbar(state).apply(Styles.flexStatic, viewWidthMod)
          )
        }
      },

      //TODO: combine with second rx! but it does not work because it would not overlay everthing as it does now.
      Rx {
        VDomModifier.ifTrue(viewIsContent()) (
          if (state.isLoading()) spaceFillingLoadingAnimation(state).apply(position.absolute, zIndex := ZIndex.loading, backgroundColor := Colors.contentBg)
          else if (state.pageNotFound()) PageNotFoundView(state).apply(position.absolute, zIndex := ZIndex.loading, backgroundColor := state.pageStyle().bgLightColor)
          else VDomModifier.empty
        )
      },

      // It is important that the view rendering is in a separate Rx.
      // This avoids rerendering the whole view when only the screen-size changed
      div(
        cls := "main-viewrender",
        id := "main-viewrender",
        viewWidthMod,

        Styles.flex,
        Styles.growFull,

        div(
          Styles.flex,
          Styles.growFull,
          cls := "pusher",
          Rx {
            val viewConfig = state.viewConfig()

            ViewRender(state, state.toFocusState(viewConfig), viewConfig.view).apply(
              Styles.growFull,
              flexGrow := 1
            ).prepend(
                overflow.visible, // we set a default overflow. we cannot just set it from outside, because every view might have a differnt nested area that is scrollable. Example: Chat which has an input at the bottom and the above history is only scrollable.
              )
            // we can now assume, that every page parentId is contained in the graph
          },
        ),
      ),
    )
  }

  def spaceFillingLoadingAnimation(state: GlobalState)(implicit data: Ctx.Data): VNode = {
    div(
      Styles.flex,
      alignItems.center,
      justifyContent.center,
      flexDirection.column,
      Styles.growFull,

      WoostLogoComponents.woostLoadingAnimationWithFadeIn,

      div(
        cls := "animated-late-fadein",
        Styles.flex,
        alignItems.center,

        fontSize.xSmall,
        marginTop := "20px",

        span("Loading forever?", marginRight := "10px"),
        button(margin := "0px", cls := "ui button compact mini", freeSolid.faRedo, " Reload", cursor.pointer, onClick.stopPropagation.foreach { dom.window.location.reload() })
      )
    )
  }

}
