package wust.webApp

import fontAwesome._
import org.scalajs.dom.experimental.permissions.PermissionState
import outwatch.dom._
import outwatch.dom.dsl._
import rx.{Ctx, Rx}
import wust.graph.Node.User
import wust.graph._
import wust.webApp.jsdom.Notifications
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views._
import wust.webUtil.Elements
import wust.webUtil.outwatchHelpers._

case class NotificationState(
  permissionState: PermissionState,
  icon: IconLookup,
  description: String,
  changes: GraphChanges,
  changesOnSuccessPrompt: Boolean,
)

//sealed trait NotificationState {
//  val permissionState: PermissionState
//  val icon: IconLookup
//  val description: String
//  val changes: GraphChanges
//  val changesOnSuccessPrompt: Boolean
//}
//case class Prompt(
//  permissionState: PermissionState,
//  icon: IconLookup,
//  description: String,
//  changes: GraphChanges,
//  changesOnSuccessPrompt: Boolean,
//) extends NotificationState
//case class Granted(
//  permissionState: PermissionState,
//  icon: IconLookup,
//  description: String,
//  changes: GraphChanges,
//  changesOnSuccessPrompt: Boolean,
//) extends NotificationState
//case class Denied(
//  permissionState: PermissionState,
//  icon: IconLookup,
//  description: String,
//  changes: GraphChanges,
//  changesOnSuccessPrompt: Boolean,
//) extends NotificationState

object WoostNotification {

  private def decorateNotificationIcon(state: GlobalState, notification: NotificationState, text: String)(implicit ctx: Ctx.Owner): VDomModifier = {
    val default = "default".asInstanceOf[PermissionState]

    VDomModifier(
      notification.permissionState match {
        case PermissionState.granted            => VDomModifier(
          Components.icon(notification.icon),
          title := notification.description,
          onClick(notification.changes) --> state.eventProcessor.changes
        )
        case PermissionState.prompt | `default` => VDomModifier(
          Components.icon(Components.iconWithIndicator(notification.icon, freeRegular.faQuestionCircle, "black")),
          title := "Notifications are currently disabled. Click to enable.",
          onClick foreach {
            Notifications.requestPermissionsAndSubscribe {
              if(notification.changesOnSuccessPrompt) state.eventProcessor.changes.onNext(notification.changes)
            }
          },
        )
        case PermissionState.denied             => VDomModifier(
          Components.icon(Components.iconWithIndicator(notification.icon, freeRegular.faTimesCircle, "tomato")),
          title := s"${notification.description} (Notifications are blocked by your browser. Please reconfigure your browser settings for this site.)",
          onClick(notification.changes) --> state.eventProcessor.changes
        )
      },
      span(text),
      cursor.pointer
    )
  }

  private def notifyControl(graph: Graph, user: User, permissionState: PermissionState, channel: Node)(implicit ctx: Ctx.Owner) = {

    val channelIdx = graph.idToIdxOrThrow(channel.id)
    val userIdx = graph.idToIdxOrThrow(user.id)
    val hasNotifyEdge = graph.notifyByUserIdx(userIdx).contains(channelIdx)

    val notify = if(hasNotifyEdge) NotificationState(
      permissionState = permissionState,
      icon = Icons.notificationsEnabled,
      description = "You are watching this node and will be notified about changes. Click to stop watching.",
      changes = GraphChanges.disconnect(Edge.Notify)(channel.id, user.id),
      changesOnSuccessPrompt = false
    ) else {
      val canNotifyParents = graph
        .ancestorsIdxExists(channelIdx)(idx => graph.notifyByUserIdx(userIdx).contains(idx))

      if(canNotifyParents) NotificationState(
        permissionState = permissionState,
        icon = Icons.notificationsEnabled,
        description = "You are not watching this node explicitly, but you watch a parent and will be notified about changes. Click to start watching this node explicitly.",
        changes = GraphChanges.connect(Edge.Notify)(channel.id, user.id),
        changesOnSuccessPrompt = true
      ) else NotificationState(
        permissionState = permissionState,
        icon = Icons.notificationsDisabled,
        description = "You are not watching this node and will not be notified. Click to start watching.",
        changes = GraphChanges.connect(Edge.Notify)(channel.id, user.id),
        changesOnSuccessPrompt = true
      )
    }

    notify
  }

  def generateNotificationItem(state: GlobalState, permissionState: PermissionState, graph: Graph, user: User, channel: Node)(implicit ctx: Ctx.Owner): VDomModifier = {

    val channelIdx = graph.idToIdxOrThrow(channel.id)
    val userIdx = graph.idToIdxOrThrow(user.id)

    @inline def permissionGranted = permissionState == PermissionState.granted
    @inline def hasNotifyEdge = graph.notifyByUserIdx(userIdx).contains(channelIdx)

    val text = if(permissionGranted && hasNotifyEdge) "Mute" else "Unmute"

    a(
      cls := "item",
      decorateNotificationIcon(state, notifyControl(graph, user, permissionState, channel), text),
    )
  }

  def banner(state: GlobalState, permissionState: PermissionState, projectName: Option[String])(implicit ctx: Ctx.Owner): VDomModifier = Rx {
    if(!state.askedForNotifications() && permissionState == PermissionState.prompt) {
      def mobileText = div(
        marginLeft.auto,
        s"Enable ", span("notifications", textDecoration.underline),
      )
      def desktopText = div(
      marginLeft.auto,
        s"Click here to ", span("enable notifications.", textDecoration.underline),
      )

      div(
        Elements.topBanner(Some(desktopText), Some(mobileText)),
        onClick foreach {
          Notifications.requestPermissionsAndSubscribe()
          state.askedForNotifications() = true
        },

        div(
          freeSolid.faTimes,
          onClick.stopPropagation(true) --> state.askedForNotifications,
          marginLeft.auto,
          marginRight := "10px"
        )
      )
    }
    else
      VDomModifier.empty
    //        "Notifications are blocked by your browser. Please reconfigure your browser settings for this site.",
  }

}

