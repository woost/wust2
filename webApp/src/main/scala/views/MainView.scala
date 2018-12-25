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

    val notificationBanner = Rx { WoostNotification.banner(state, state.permissionState()) }

    val emailBanner = Rx { // TODO: we need this for the migration from username to email - can go away afterwards
      Observable.fromFuture(
        state.user() match {
          case user: AuthUser.Real =>
            Client.auth.getUserDetail(user.id).map {
              case Some(detail) =>
                VDomModifier.ifTrue(detail.email.isEmpty) {
                  val desktopText = div(
                    span("You do not have an email setup. "),
                    span("Please update your profile.", textDecoration.underline),
                    span(" After the 7th of Januar 2019 you can only login via email."),
                  )
                  val mobileText = div(
                    span("Please setup an email in "),
                    span("your profile", textDecoration.underline),
                    span(" until the 7th of Januar 2019."),
                  )

                  div(
                    Elements.topBanner(Some(desktopText), Some(mobileText)),
                    onClick(state.viewConfig.now.showViewWithRedirect(View.UserSettings)) --> state.viewConfig,
                  )
                }
              case err                   =>
                scribe.info(s"Cannot get user details: ${ err }")
                VDomModifier.empty
            }
          case _                   =>
            Future.successful(VDomModifier.empty)
        }
      )
    }

    val registerBanner = Rx {
      state.page().parentId.map{ pageParentId =>
        state.user() match {
          case _: AuthUser.Real                => VDomModifier.empty
          case user if user.id == pageParentId =>
            val mobileText = div(
              "Signup",
              textDecoration.underline
            )

            div(
              Elements.topBanner(None, Some(mobileText)),
              onClick(state.viewConfig.now.showViewWithRedirect(View.Signup)) --> state.viewConfig,
              onClick foreach {
                Analytics.sendEvent("banner", "signuponuser")
              },
            )
          case _ => VDomModifier.empty
        }
      }
    }

    VDomModifier(
      cls := "mainview",
      Styles.flex,
//      DevOnly { DevView(state) },
      UI.modal(state.modalConfig), // one modal instance for the whole page that can be configured via state.modalConfig
      div(
        cls := "topBannerContainer",
        notificationBanner,
        registerBanner,
        emailBanner,
      ),
      Topbar(state)(width := "100%", Styles.flexStatic),
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
              if (state.isLoading()) Components.customLoadingAnimation(state).apply(position.absolute, zIndex := ZIndex.loading, backgroundColor := state.pageStyle.now.bgLightColor)
              else if (state.pageNotFound()) PageNotFoundView(state).apply(position.absolute, zIndex := ZIndex.loading, backgroundColor := state.pageStyle.now.bgLightColor, Styles.growFull)
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
