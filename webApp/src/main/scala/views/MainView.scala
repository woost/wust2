package wust.webApp.views

import googleAnalytics.Analytics
import monix.reactive.Observable
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api.AuthUser
import wust.css.{Styles, ZIndex}
import wust.graph.Page
import wust.util._
import wust.webApp.{Client, DevOnly, WoostNotification}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize, View}
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

    val notificationBanner = Rx {
      val projectName = state.page().parentId.map(pid => state.graph().nodesById(pid).str)
      WoostNotification.banner(state, state.permissionState(), projectName)
    }

    VDomModifier(
      cls := "mainview",
      Styles.flex,
//      DevOnly { DevView(state) },
      UI.modal(state.modalConfig, state.page.toLazyTailObservable.map(_ => ())), // one modal instance for the whole page that can be configured via state.modalConfig
      div(id := "draggable-mirrors", position.absolute), // draggable mirror is positioned absolute, so that mirrors do not affect the page layout (especially flexbox) and therefore do not produce ill-sized or misplaced mirrors
      div(
        cls := "topBannerContainer",
        notificationBanner,
      ),
      Rx { VDomModifier.ifTrue(state.topbarIsVisible())(Topbar(state)(width := "100%", Styles.flexStatic)) },
      div(
        Styles.flex,
        Styles.growFull,
        position.relative, // needed for mobile expanded sidebar
        Sidebar(state),
        backgroundColor <-- state.pageStyle.map(_.bgColor),
        div(
          Styles.flex,
          Styles.growFull,
          flexDirection.column,
          overflow.auto,
          position.relative, // important for position absolute of loading animation to have the correct width of its parent element
          {
            val breadCrumbs = Rx {
              VDomModifier.ifTrue(state.pageHasParents())(BreadCrumbs(state)(Styles.flexStatic))
            }
            val viewIsContent = Rx {
              state.view().isContent
            }
            Rx {
              VDomModifier.ifTrue(viewIsContent())(
                breadCrumbs,
                PageHeader(state).apply(Styles.flexStatic)
              )
            }
          },
          Rx {
            if(state.view().isContent) {
              if (state.isLoading())
                Components.spaceFillingLoadingAnimation(state).apply(position.absolute, zIndex := ZIndex.loading, backgroundColor := state.pageStyle.now.bgLightColor)
              else if (state.pageNotFound())
                PageNotFoundView(state).apply(position.absolute, zIndex := ZIndex.loading, backgroundColor := state.pageStyle.now.bgLightColor, Styles.growFull)
              else VDomModifier.empty
            } else VDomModifier.empty
          },
          // It is important that the view rendering is in a separate Rx.
          // This avoids rerendering the whole view when only the screen-size changed
          Rx {
            // we can now assume, that every page parentId is contained in the graph
            ViewRender(state.view(), state).apply(
              Styles.growFull,
              flexGrow := 1
            ).prepend(
              overflow.visible
            )
          },
        )
      )
    )
  }
}
