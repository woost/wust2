package wust.pwaApp

import wust.utilWeb._
import wust.utilWeb.views._
import outwatch.dom._
import outwatch.dom.dsl._
import wust.utilWeb.outwatchHelpers._
import rx._

object MainView {
  import MainViewParts._

  val settings: VNode = {
    val notificationsHandler = Handler.create[Unit](()).unsafeRunSync()
    div(
      child <-- notificationsHandler.map { _ =>
        if (Notifications.isGranted) Option.empty[VNode]
        else if (Notifications.isDenied) Some(
          span("Notifications are blocked", title := "To enable Notifications for this site, reconfigure your browser to not block Notifications from this domain.")
        ) else Some(
          button("Enable Notifications", onClick --> sideEffect {
            Notifications.requestPermissions().foreach { _ =>
              notificationsHandler.unsafeOnNext(())
            }
          })
        )
      }
    )
  }

  val pushNotifications = button("enable push", onClick --> sideEffect { Notifications.subscribeWebPush() })

  def sidebar(state: GlobalState): VNode = {
    div(
      width := "175px",
      backgroundColor <-- state.pageStyle.map(_.darkBgColor.toHex),
      padding := "10px",
      color := "white",
      titleBanner,
      br(),
      userStatus(state),
      br(),
      settings,
      br(),
      pushNotifications,
      br(),
      syncStatus(state)
    )
  }

  def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    div(
      height := "100%",
      width := "100%",
      display.flex,
      sidebar(state),
      ChatView(state)(width := "100%")
    )
  }
}
