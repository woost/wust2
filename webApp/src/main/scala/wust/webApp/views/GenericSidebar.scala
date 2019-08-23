package wust.webApp.views

import wust.facades.fomanticui.{JQuerySelectionWithFomanticUI, SidebarOptions}
import monix.execution.Cancelable
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Ownable}
import wust.css.ZIndex

import scala.scalajs.js.JSConverters._

object GenericSidebar {

  final case class Config(mainModifier: VDomModifier = VDomModifier.empty, openModifier: VDomModifier = VDomModifier.empty, overlayOpenModifier: VDomModifier = VDomModifier.empty, expandedOpenModifier: VDomModifier = VDomModifier.empty, closedModifier: Option[VDomModifier] = None)

  def left(sidebarOpen: Var[Boolean], isFullscreen: Var[Boolean], config: Ownable[Config]): VNode = apply(isRight = false, sidebarOpen = sidebarOpen, isFullscreen = isFullscreen, config = config)
  def right(sidebarOpen: Var[Boolean], isFullscreen: Var[Boolean], config: Ownable[Config]): VNode = apply(isRight = true, sidebarOpen = sidebarOpen, isFullscreen = isFullscreen, config = config)

  private def apply(isRight: Boolean, sidebarOpen: Var[Boolean], isFullscreen: Var[Boolean], config: Ownable[Config]): VNode = {
    def openSwipe = (if (isRight) onSwipeLeft(true) else onSwipeRight(true)) --> sidebarOpen
    def closeSwipe = (if (isRight) onSwipeRight(false) else onSwipeLeft(false)) --> sidebarOpen
    def directionOverlayModifier = if (isRight) cls := "overlay-right-sidebar" else cls := "overlay-left-sidebar"
    def directionExpandedModifier = if (isRight) cls := "expanded-right-sidebar" else cls := "expanded-left-sidebar"

    def closedSidebar(config: Config) = VDomModifier(
      config.closedModifier.map { closedModifier =>
        VDomModifier(
          cls := "sidebar",
          VDomModifier.ifTrue(BrowserDetect.isMobile)(openSwipe),
          closedModifier
        )
      }
    )

    def openSidebar(config: Config) = VDomModifier(
      cls := "sidebar sidebar-open",
      config.openModifier
    )

    def overlayOpenSidebar(config: Config) = VDomModifier(
      cls := "overlay-sidebar",
      directionOverlayModifier,
      onClick.onlyOwnEvents(false) --> sidebarOpen,
      VDomModifier.ifTrue(BrowserDetect.isMobile)(closeSwipe),

      div(
        openSidebar(config),
        config.overlayOpenModifier,
      )
    )

    def sidebarWithOverlay(config: Config)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
      closedSidebar(config),
      Rx {
        sidebarOpen() match {
          case true  => div(overlayOpenSidebar(config))
          case false => VDomModifier.empty
        }
      }
    )

    def sidebarWithExpand(config: Config)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
      cls := "expanded-sidebar",
      directionExpandedModifier,
      VDomModifier.ifTrue(BrowserDetect.isMobile)(closeSwipe),
      div(
        Rx {
          sidebarOpen() match {
            case true  => VDomModifier(openSidebar(config), config.expandedOpenModifier)
            case false => VDomModifier(closedSidebar(config))
          }
        }
      )
    )

    // TODO make static again. currently not because outer subscriptions on the callsite do not like us not rerendering (looking at you: GraphChangesAutomationUI). MainView is fine with it.
    div(config.flatMap(config => Ownable { implicit ctx =>
      VDomModifier(
        if (BrowserDetect.isMobile) sidebarWithOverlay(config)
        else isFullscreen.map {
          case true => sidebarWithOverlay(config)
          case false => sidebarWithExpand(config)
        },
        config.mainModifier
      )
    }))
  }

  final case class SidebarConfig(items: VDomModifier, sidebarModifier: VDomModifier = VDomModifier.empty)
  def sidebar(config: Observable[Ownable[SidebarConfig]], globalClose: Observable[Unit], targetSelector: Option[String]): VNode = {
    val elemHandler = PublishSubject[JQuerySelectionWithFomanticUI]

    div(
      cls := "ui sidebar right icon labeled borderless vertical menu mini",
      //      width := (if (BrowserDetect.isMobile) "90%" else "400px"),
      width := "160px",
      zIndex := ZIndex.uiSidebar,

      config.map[VDomModifier] { config =>
        config.flatMap[VDomModifier](config => Ownable { implicit ctx =>
          VDomModifier(
            emitter(globalClose.take(1)).useLatest(onDomMount.asJquery).foreach { e =>
              e.sidebar("hide")
              // TODO: remove this node from the dom whenever it is hidden (make this thing an observable[option[ownable[sidebarconfig]]]
              // workaround: kill the ctx owner, so we stop updating this node when it is closed.
              ctx.contextualRx.kill()
            },
            managedElement.asJquery { e =>
              elemHandler.onNext(e)
              e.sidebar(new SidebarOptions {
                transition = "overlay"
                mobileTransition = "overlay"
                exclusive = true
                context = targetSelector.orUndefined
              }).sidebar("show")

              Cancelable(() => e.sidebar("destroy"))
            },
            config.items,
            config.sidebarModifier
          )
        })
      }
    )
  }

}
