package wust.webApp.views

import fontAwesome.freeSolid
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.BrowserDetect
import wust.webUtil.outwatchHelpers._
import wust.css.{ Styles, ZIndex }
import wust.sdk.Colors
import wust.webApp.WoostNotification
import wust.webApp.state.{ GlobalState, ScreenSize }
import wust.webApp.views.Components._
import wust.facades.wdtEmojiBundle.wdtEmojiBundle
import wust.ids.Feature
import wust.webApp.state.FeatureDetails
import wust.webApp.DevOnly

object MainView {

  def apply(implicit ctx: Ctx.Owner): VNode = {
    div(
      // DevOnly {
      //   featureConsistencyChecks,
      // },
      Styles.growFull,
      Rx {
        if (GlobalState.hasError()) ErrorPage()
        else main
      }
    )
  }

  private def main(implicit ctx: Ctx.Owner): VDomModifier = {
    VDomModifier(
      div(
        Styles.flex,
        Styles.growFull,

        position.relative, // needed for mobile expanded sidebars

        onMouseDown(()) --> GlobalState.mouseClickInMainView,

        LeftSidebar.apply(
          onMouseDown(None) --> GlobalState.rightSidebarNode,
        ),

        div(
          Styles.flex,
          Styles.growFull,
          flexDirection.column,
          onMouseDown(None) --> GlobalState.rightSidebarNode,

          //      DevOnly { DevView },
          topBannerContainer,
          content,
        ),
        VDomModifier.ifNot(BrowserDetect.isMobile)(EmojiPicker()),

        RightSidebar(ViewRender),
      )
    )
  }

  def topBannerContainer(implicit ctx: Ctx.Owner) = {
    val projectName = Rx {
      GlobalState.page().parentId.map(pid => GlobalState.graph().nodesByIdOrThrow(pid).str)
    }

    div(
      cls := "topBannerContainer",
      Rx {
        WoostNotification.banner(GlobalState.permissionState(), projectName())
      }
    )
  }

  def content(implicit ctx: Ctx.Owner) = {
    val viewIsContent = Rx {
      GlobalState.view().isContent
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
        if (viewIsContent())
          PageHeader(ViewRender).apply(Styles.flexStatic, viewWidthMod)
        else {
          VDomModifier.ifTrue(GlobalState.screenSize() != ScreenSize.Small)(
            Topbar.apply(Styles.flexStatic, viewWidthMod)
          )
        }
      },

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
            val viewConfig = GlobalState.viewConfig()

            if (viewIsContent() && GlobalState.isLoading()) {
              spaceFillingLoadingAnimation.apply(Styles.growFull, zIndex := ZIndex.loading, backgroundColor := Colors.contentBg)
            } else if (viewIsContent() && GlobalState.pageNotFound()) {
              PageNotFoundView.apply.apply(Styles.growFull, zIndex := ZIndex.loading, backgroundColor := Colors.contentBg)
            } else {
              ViewRender(GlobalState.toFocusState(viewConfig), viewConfig.view).apply(
                Styles.growFull,
                flexGrow := 1
              ).prepend(
                  overflow.visible, // we set a default overflow. we cannot just set it from outside, because every view might have a differnt nested area that is scrollable. Example: Chat which has an input at the bottom and the above history is only scrollable.
              )
            }
          },
        ),
      ),
    )
  }

  def spaceFillingLoadingAnimation(implicit ctx: Ctx.Owner): VNode = {
    div(
      Styles.flex,
      alignItems.center,
      justifyContent.center,
      flexDirection.column,
      Styles.growFull,

      WoostLogoComponents.woostLoadingAnimationWithFadeIn,

      div(
        Styles.flex,
        alignItems.center,

        fontSize.xSmall,
        marginTop := "20px",

        Rx {
          if (GlobalState.isClientOnline())
            div(
              cls := "animated-late-fadein",
              span("Loading forever?", marginRight := "10px"),
              Components.reloadButton
            )
          else
            div(
              cls := "animated-alternating-fade",
              span("CONNECTING"),
            )
        }
      )
    )
  }

  private def featureConsistencyChecks = {
    div(
      overflow.auto,
      Styles.flex,
      height := "100px",
      Styles.flexStatic,
      // runtime consistency checks for features
      div(
        h3("Features not reachable by suggestions:"),
        Feature.unreachable.map(feature => div(feature.toString)),
      ),
      div(
        h3("Missing feature details:"),
        marginLeft := "20px",
        FeatureDetails.missingDetails.sortBy(_.toString).map(feature => div(feature.toString)),
      ),
    )
  }
}
