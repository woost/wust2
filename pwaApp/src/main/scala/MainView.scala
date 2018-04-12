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

  def buttonStyle = Seq(
    width := "100%",
    padding := "5px 3px",
  )

  val notificationSettings: VNode = {
    div(
      Notifications.permissionStateObservable.map { state =>
        if (state == PermissionState.granted) Option.empty[VNode]
        else if (state == PermissionState.denied) Some(
          span("Notifications blocked", title := "To enable Notifications for this site, reconfigure your browser to not block Notifications from this domain.")
        ) else Some(
          button("Enable Notifications",
            padding := "5px 3px",
            width := "100%",
            onClick --> sideEffect { Notifications.requestPermissions() },
            buttonStyle
          )
        )
      }
    )
  }

  def sidebar(state: GlobalState)(implicit owner:Ctx.Owner): Rx[VNode] = {
      Rx {
        if(state.sidebarOpen()) {
          div(
            backgroundColor <-- state.pageStyle.map(_.darkBgColor.toHex),
            div(cls := "hamburger", margin := "7px", onClick(false) --> state.sidebarOpen),
            id := "sidebar",
            flexBasis := "175px",
            color := "white",
            div(padding := "8px 8px", titleBanner(syncStatus(state)(owner)(fontSize := "9px"))),
            undoRedo(state),
            br(),
            channels(state),
            br(),
            newGroupButton(state)(owner)(buttonStyle),
            // userStatus(state),
            notificationSettings,
            overflowY.auto
          )
        } else {
          div(
            backgroundColor <-- state.pageStyle.map(_.darkBgColor.toHex),
            div(cls := "hamburger", margin := "7px", onClick(true) --> state.sidebarOpen),
            id := "sidebar",
            flexBasis := "30px",
            color := "white",
            channelIcons(state),
            overflowY.auto
          )
        }
      }
  }

  def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    div(
      height := "100%",
      width := "100%",
      display.flex,
      sidebar(state)(owner).map(_(flexGrow := 0, flexShrink := 0)),
      backgroundColor <-- state.pageStyle.map(_.bgColor.toHex),
      Rx {
        if (state.page().parentIds.nonEmpty) {
          state.view().apply(state)(owner)(flexGrow := 1)
        } else {
          div(
            flexGrow := 1,
            display.flex, justifyContent.spaceAround, flexDirection.column, alignItems.center,
            newGroupButton(state)(owner)(padding := "20px", marginBottom := "10%")
          )
        }
      }
    )
  }
}
