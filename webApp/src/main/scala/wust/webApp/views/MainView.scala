package wust.webApp.views

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.console
import outwatch._
import outwatch.dsl._
import colibri.ext.rx._
import rx._
import wust.css.{ Styles, ZIndex }
import wust.ids.Feature
import wust.sdk.Colors
import wust.webApp.WoostNotification
import wust.webApp.state.{ FeatureDetails, GlobalState, PresentationMode, ScreenSize, FocusState, FocusPreference, ViewConfig }
import wust.webUtil.Elements._
import wust.webUtil.{ BrowserDetect, UI }
import wust.webUtil.outwatchHelpers._
import wust.facades.segment.Segment
import fontAwesome.freeSolid
import org.scalajs.dom.raw.MouseEvent
import wust.webUtil.Elements

object MainView {

  def apply(implicit ctx: Ctx.Owner): VNode = {
    div(
      cls <-- GlobalState.screenSize.map {
        case ScreenSize.Small  => "screensize-small"
        case ScreenSize.Middle => "screensize-middle"
        case ScreenSize.Large  => "screensize-large"
      },
      // DevOnly {
      //   featureConsistencyChecks,
      // },
      Styles.growFull,
      Rx {
        if (GlobalState.hasError()) ErrorPage()
        else GlobalState.presentationMode() match {
          case PresentationMode.Full              => fullPresentation
          case mode: PresentationMode.Alternative => presentation(mode)
        }
      }
    )
  }

  private val onMouseDownCloseRightSidebar = onMouseDown.use(None) --> GlobalState.rightSidebarNode


  private def presentation(mode: PresentationMode.Alternative)(implicit ctx: Ctx.Owner): VDomModifier = {
    div(
      Styles.growFull,
      Styles.flex,
      flexDirection.column,

      div(
        Styles.flex,
        Styles.growFull,

        onMouseDown.use(()) --> GlobalState.mouseClickInMainView,

        div(
          Styles.flex,
          Styles.growFull,
          flexDirection.column,

          onMouseDownCloseRightSidebar,

          content(PresentationMode.ContentOnly),
        ),

        VDomModifier.ifNot(BrowserDetect.isMobile)(EmojiPicker()),

        RightSidebar(ViewRender),
      ),

      ScreenSize.dontShowOnSmallScreen(
        mode match {
          case PresentationMode.ContentOnly => advancedUIBanner
          case PresentationMode.ThreadTracker => advancedUIBanner
          case _                            => VDomModifier.empty
        }
      )
    )
  }

  private val advancedUIBanner = {
    div(
      Styles.flex,
      alignItems.center,
      backgroundColor := "#494653",
      color := "white",
      div(
        marginLeft := "10px",
        cls := "hover-full-opacity",
        "Show advanced UI",
        onClickDefault.foreach {
          GlobalState.urlConfig.update(_.copy(mode = PresentationMode.Full))
        },
        marginRight := "auto",
      ),
      div(
        marginLeft := "auto",
        marginLeft := "10px",
        cls := "hover-full-opacity",
        "Got feedback? Please tell us!",
        onClickDefault.foreach {
          FeedbackForm.openCrispChat
        },
        marginRight := "auto",
      ),
      div(
        Styles.flex,
        alignItems.center,
        span(
          padding := "6px",
          "Powered by ",
        ),
        WoostLogoComponents.woostIcon.apply(width := "17px", height := "17px", color := "#ae7eff"),
        span(
          padding := "6px 10px 6px 1px",
          b("Woost")
        ),
        // onClickDefault.foreach(GlobalState.urlConfig.update(_.copy(mode = PresentationMode.Full)))
        onClickDefault.foreach{ _ =>
          Segment.trackEvent("ClickedPresentationModeBanner")
          dom.window.open("https://woost.space", "_blank")
        }
      )
    )
  }

  private def fullPresentation(implicit ctx: Ctx.Owner): VDomModifier = {
    div(
      Styles.flex,
      Styles.growFull,

      position.relative, // needed for mobile expanded sidebars

      onMouseDown.use(()) --> GlobalState.mouseClickInMainView,

      LeftSidebar(),

      div(
        Styles.flex,
        Styles.growFull,
        flexDirection.column,
        onMouseDownCloseRightSidebar,

        //      DevOnly { DevView },
        topBannerContainer,
        content(PresentationMode.Full),
      ),

      VDomModifier.ifNot(BrowserDetect.isMobile)(EmojiPicker()),

      RightSidebar(ViewRender),
    )
  }

  def topBannerContainer(implicit ctx: Ctx.Owner) = {
    div(
      cls := "topBannerContainer",
      Rx {
        if (GlobalState.automationIsDisabled()) WoostNotification.automationIsDisabledBanner
        else WoostNotification.banner(GlobalState.permissionState())
      }
    )
  }

  def content(presentationMode: PresentationMode)(implicit ctx: Ctx.Owner) = {
    val viewIsContent = Rx {
      GlobalState.view().isContent
    }

    // a view should never be shrinked to less than 300px-45px collapsed sidebar
    val viewWidthMod = minWidth := s"${300 - LeftSidebar.minWidthSidebar}px"

    val viewAndPage = Rx {
      val viewConfig = GlobalState.viewConfig()
      (viewConfig.view, viewConfig.page)
    }

    div(
      Styles.flex,
      Styles.growFull,

      flexDirection.column,
      overflow.auto,
      Elements.onMouseDownOverHorizontalScrollbarStopPropagation,
      Elements.onMouseDownOverVerticalScrollbarStopPropagation,
      position.relative, // important for position absolute of loading animation to have the correct width of its parent element

      backgroundColor := Colors.contentBg,

      Rx {
        if (viewIsContent())
          PageHeader(ViewRender).apply(Styles.flexStatic, viewWidthMod)
        else {
          VDomModifier.ifTrue(GlobalState.screenSize() != ScreenSize.Small && presentationMode == PresentationMode.Full)(
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
            val view = viewAndPage()._1 // reacting to view and page. both will be passed into rendered views via focuState
            if (viewIsContent() && GlobalState.isLoading()) {
              spaceFillingLoadingAnimation.apply(Styles.growFull, zIndex := ZIndex.loading, backgroundColor := Colors.contentBg)
            } else if (GlobalState.showPageNotFound()) {
              PageNotFoundView.apply.apply(Styles.growFull, zIndex := ZIndex.loading, backgroundColor := Colors.contentBg)
            } else {
              ViewRender(GlobalState.mainFocusState(GlobalState.viewConfig.now), view).apply(
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

        GlobalState.isClientOnline.distinctOnEquals.map {
          case true => div(
            cls := "animated-late-fadein",
            span("Loading forever?", marginRight := "10px"),
            Components.reloadButton
          )
          case false => div(
            cls := "animated-alternating-fade",
            span("CONNECTING"),
          )
        }
      )
    )
  }

  private def featureConsistencyChecks = {
    VDomModifier.ifTrue(Feature.unreachable.nonEmpty || FeatureDetails.missingDetails.nonEmpty)(
      div(
        overflow.auto,
        Styles.flex,
        height := "150px",
        Styles.flexStatic,
        // runtime consistency checks for features
        div(
          h3("Features not reachable by suggestions (", Feature.unreachable.size, "):"),
          UI.progress(Feature.allWithoutSecrets.size - Feature.unreachable.size, Feature.allWithoutSecrets.size, classes = "indicating"),
          Feature.unreachable.map(feature => div(feature.toString)),
        ),
        div(
          h3("Missing feature details (", FeatureDetails.missingDetails.size, "):"),
          UI.progress(Feature.all.size - FeatureDetails.missingDetails.size, Feature.all.size, classes = "indicating"),
          marginLeft := "20px",
          FeatureDetails.missingDetails.sortBy(_.toString).map(feature => div(feature.toString)),
        ),
      )
    )
  }
}
