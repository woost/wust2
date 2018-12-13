package wust.webApp

import fontAwesome._
import org.scalajs.dom.experimental.permissions.PermissionState
import outwatch.dom._
import outwatch.dom.dsl._
import rx.Ctx
import wust.css
import wust.graph.Node.User
import wust.graph._
import wust.util._
import wust.webApp.jsdom.Notifications
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views._

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

  def decorateNotificationIcon(state: GlobalState, notification: NotificationState)(implicit ctx: Ctx.Owner): VDomModifier = {
    val default = "default".asInstanceOf[PermissionState]
    notification.permissionState match {
      case PermissionState.granted            => VDomModifier(
        (notification.icon: VNode) (cls := "fa-fw"),
        title := notification.description,
        onClick(notification.changes) --> state.eventProcessor.changes
      )
      case PermissionState.prompt | `default` => VDomModifier(
        Elements.iconWithIndicator(notification.icon, freeRegular.faQuestionCircle, "black")(cls := "fa-fw"),
        title := "Notifications are currently disabled. Click to enable.",
        onClick foreach {
          Notifications.requestPermissionsAndSubscribe {
            if(notification.changesOnSuccessPrompt) state.eventProcessor.changes.onNext(notification.changes)
          }
        },
      )
      case PermissionState.denied             => VDomModifier(
        Elements.iconWithIndicator(notification.icon, freeRegular.faTimesCircle, "tomato")(cls := "fa-fw"),
        title := s"${notification.description} (Notifications are blocked by your browser. Please reconfigure your browser settings for this site.)",
        onClick(notification.changes) --> state.eventProcessor.changes
      )
    }
  }

  private def notifyControl(graph: Graph, user: User, permissionState: PermissionState, channel: Node)(implicit ctx: Ctx.Owner) = {

    val channelIdx = graph.idToIdx(channel.id)
    val userIdx = graph.idToIdx(user.id)
    val hasNotifyEdge = graph.notifyByUserIdx(userIdx).contains(channelIdx)

    val notify = if(hasNotifyEdge) NotificationState(
      permissionState = permissionState,
      icon = Icons.notificationsEnabled,
      description = "You are watching this node and will be notified about changes. Click to stop watching.",
      changes = GraphChanges.disconnect(Edge.Notify)(channel.id, user.id),
      changesOnSuccessPrompt = false
    ) else {
      val canNotifyParents = graph
        .ancestorsIdx(channelIdx)
        .exists(idx => graph.notifyByUserIdx(userIdx).contains(idx))

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

  def generateNotificationItem(state: GlobalState, permissionState: PermissionState, graph: Graph, user: User, channel: Node, isOwnUser: Boolean)(implicit ctx: Ctx.Owner): VDomModifier = {

    val channelIdx = graph.idToIdx(channel.id)
    val userIdx = graph.idToIdx(user.id)

    @inline def permissionGranted = permissionState == PermissionState.granted
    @inline def hasNotifyEdge = graph.notifyByUserIdx(userIdx).contains(channelIdx)

    val text = if(permissionGranted && hasNotifyEdge) "Mute" else "Unmute"

    (!isOwnUser).ifTrue[VDomModifier](div(
      cls := "item",
      decorateNotificationIcon(state, notifyControl(graph, user, permissionState, channel)),

      span(marginLeft := "7px", cls := "text", text, cursor.pointer)
    ))
  }

  def banner(state: GlobalState, permissionState: PermissionState): VDomModifier = {
    //        "Notifications are blocked by your browser. Please reconfigure your browser settings for this site.",
    if(permissionState == PermissionState.prompt)
      Elements.topBanner(
        "Woost needs your permission to",
        span("enable notifications.", paddingLeft := "1ch", textDecoration.underline),
        onClick foreach {
          Notifications.requestPermissionsAndSubscribe { }
        },
      )
    else
      VDomModifier.empty
  }

}

