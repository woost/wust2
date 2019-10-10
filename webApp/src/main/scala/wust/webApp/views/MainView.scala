package wust.webApp.views

import wust.webUtil.UI
import fontAwesome.freeSolid
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.ext.monix._
import rx._
import wust.webUtil.BrowserDetect
import wust.webUtil.outwatchHelpers._
import wust.css.{ Styles, ZIndex }
import wust.sdk.Colors
import wust.webApp.WoostNotification
import wust.webApp.state.{ GlobalState, ScreenSize, PresentationMode}
import wust.webApp.views.Components._
import wust.facades.wdtEmojiBundle.wdtEmojiBundle
import wust.ids.Feature
import wust.webApp.state.FeatureDetails
import wust.webApp.DevOnly
import wust.webUtil.Elements._

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
          case PresentationMode.Full => fullPresentation
          case PresentationMode.ContentOnly => contentPresentation
        }
      }
    )
  }

  private def contentPresentation(implicit ctx: Ctx.Owner): VDomModifier = {
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

          onMouseDown.use(None) --> GlobalState.rightSidebarNode,

          content(PresentationMode.ContentOnly),
        ),

        VDomModifier.ifNot(BrowserDetect.isMobile)(EmojiPicker()),

        RightSidebar(ViewRender),
      ),

      div(
        marginLeft := "auto",
        marginRight := "10px",
        "Brought to you by ",
        b(
          color := Colors.woost,
          WoostLogoComponents.woostIcon,
          " Woost",
          // onClickDefault.foreach(GlobalState.urlConfig.update(_.copy(mode = PresentationMode.Full)))
          onClickDefault.foreach(dom.window.location.href = "https://woost.space")
        )
      )
    )
  }


  private def fullPresentation(implicit ctx: Ctx.Owner): VDomModifier = {
    div(
      Styles.flex,
      Styles.growFull,

      position.relative, // needed for mobile expanded sidebars

      onMouseDown.use(()) --> GlobalState.mouseClickInMainView,

      LeftSidebar.apply(
        onMouseDown.use(None) --> GlobalState.rightSidebarNode,
      ),

      div(
        Styles.flex,
        Styles.growFull,
        flexDirection.column,
        onMouseDown.use(None) --> GlobalState.rightSidebarNode,

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
        WoostNotification.banner(GlobalState.permissionState())
      }
    )
  }

  def content(presentationMode: PresentationMode)(implicit ctx: Ctx.Owner) = {
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
