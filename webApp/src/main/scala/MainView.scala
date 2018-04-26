package wust.webApp

import org.scalajs.dom.experimental.permissions.PermissionState
import outwatch.AsVDomModifier
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webApp.fontAwesome.freeSolid._
import wust.webApp.outwatchHelpers._

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

  def sidebar(state: GlobalState)(implicit owner:Ctx.Owner): VNode = {
    import state.sidebarOpen
    state.sidebarOpen.debug("sidopen")
    div(
      id := "sidebar",
      backgroundColor <-- state.pageStyle.map(_.darkBgColor.toHex),
      color := "white",
      transition := "flex-basis 0.2s",
      overflowY.auto,
      flexBasis <-- sidebarOpen.map { case true => "175px"; case false => "30px" },
      sidebarOpen.map {
        case true =>
          div(
            div(
              display.flex, alignItems.baseline,
              // TODO: stoppropagation is needed because of https://github.com/OutWatch/outwatch/pull/193
              div(faBars, padding := "7px", cursor.pointer, onClick --> sideEffect{ev => sidebarOpen() = false; ev.stopPropagation()}),
              div("Woost", padding := "5px 5px", fontWeight.bold, fontSize := "18px"),
              syncStatus(state)(owner)(fontSize := "9px")
            ),
            undoRedo(state),
            br(),
            channels(state),
            br(),
            newGroupButton(state)(owner)(buttonStyle),
            authentication(state),
            notificationSettings
          )
        case false =>
          div(
            div(faBars, padding := "7px", cursor.pointer, onClick(true) --> sidebarOpen),
            channelIcons(state)
          )
      }
    )
  }

  def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    div(
      height := "100%",
      width := "100%",
      display.flex,
      sidebar(state)(owner)(flexGrow := 0, flexShrink := 0),
      backgroundColor <-- state.pageStyle.map(_.bgColor.toHex),
      Rx {
        (if (state.page().parentIds.nonEmpty) {
          state.view().apply(state)
        } else {
          newGroupPage(state)
        }).apply(flexGrow := 1)
      }
    )
  }
}
