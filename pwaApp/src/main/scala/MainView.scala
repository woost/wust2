package wust.pwaApp

import org.scalajs.dom.experimental.permissions.PermissionState
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

  val notificationSettings: VNode = {
    div(
      Notifications.notificationPermissionRx.map { state =>
        if (state == PermissionState.granted) Option.empty[VNode]
        else if (state == PermissionState.denied) Some(
          span("Notifications are blocked", title := "To enable Notifications for this site, reconfigure your browser to not block Notifications from this domain.")
        ) else Some(
          button("Enable Notifications",
            padding := "5px 3px",
            width := "100%",
            onClick --> sideEffect { Notifications.requestPermissions() }
          )
        )
      }.toObservable
    )
  }

  val pushNotificationSettings: VNode = {
    div(
      Notifications.pushPermissionRx.map { state =>
        if (state == PermissionState.granted) Option.empty[VNode]
        else if (state == PermissionState.denied) Some(
          span("Push is blocked", title := "To enable Push for this site, reconfigure your browser to not block Push from this domain.")
        ) else Some(
          button("Enable Push",
            padding := "5px 3px",
            width := "100%",
            onClick --> sideEffect { Notifications.subscribeAndPersistWebPush() }
          )
        )
      }.toObservable
    )
  }

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
      notificationSettings,
      pushNotificationSettings(buttonStyle),
      overflowY.auto
    )
  }

  def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    div(
      height := "100%",
      width := "100%",
      display.flex,
      sidebar(state),
      Rx {
        if(state.inner.page().parentIds.nonEmpty) {
          ChatView(state)(owner)(width := "100%")
        } else {
          newGroupButton(state)
        }
      }.toObservable
    )
  }
}
