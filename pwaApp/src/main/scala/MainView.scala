package wust.pwaApp

import wust.utilWeb._
import wust.utilWeb.views._
import outwatch.dom._
import outwatch.dom.dsl._
import wust.utilWeb.outwatchHelpers._
import rx._
import wust.graph._
import scala.scalajs.js.Date

object MainView {
  import MainViewParts._

  val settings: VNode = {
    val notificationsHandler = Handler.create[Unit](()).unsafeRunSync()
    div(
      notificationsHandler.map { _ =>
        if (Notifications.isGranted) Option.empty[VNode]
        else if (Notifications.isDenied) Some(
          span("Notifications are blocked", title := "To enable Notifications for this site, reconfigure your browser to not block Notifications from this domain.")
        ) else Some(
          button("Enable Notifications", 
            padding := "5px 3px",
            width := "100%",
            onClick --> sideEffect {
            Notifications.requestPermissions().foreach { _ =>
              notificationsHandler.unsafeOnNext(())
            }
          })
        )
      }
    )
  }

  val pushNotifications = button("enable push", onClick --> sideEffect { Notifications.subscribeWebPush() })

  def sidebar(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    def buttonStyle = Seq(
      width := "100%",
      padding := "5px 3px",
    )

    div(
      id := "sidebar",
      width := "175px",
      backgroundColor <-- state.pageStyle.map(_.darkBgColor.toHex),
      color := "white",
      div(padding := "8px 8px", titleBanner(syncStatus(state)(fontSize := "9px"))),
      undoRedo(state),
      br(),
      channels(state),
      br(),
      newGroupButton(state)(ctx)(buttonStyle),
      // userStatus(state),
      settings,
      pushNotifications(buttonStyle),
      overflowY.auto
    )
  }

  def channels(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
      div(
        color := "#C4C4CA",
        Rx {
          state.inner.highLevelPosts().map{p => div(
            padding := "5px 3px",
            p.content, 
            cursor.pointer,
            onClick(Page(p.id)) --> state.page,
            title := p.id,
            if(state.inner.page().parentIds.contains(p.id)) Seq(
              color := state.inner.pageStyle().darkBgColor.toHex,
              backgroundColor := state.inner.pageStyle().darkBgColorHighlight.toHex)
            else Option.empty[VDomModifier]
          )}
        }.toObservable
      )
  }

  def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    div(
      height := "100%",
      width := "100%",
      display.flex,
      sidebar(state),
      ChatView(state)(owner)(width := "100%")
    )
  }
}
