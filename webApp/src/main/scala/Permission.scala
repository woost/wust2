package wust.webApp

import fontAwesome._
import googleAnalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views.Elements

case class PermissionDescription(
  access: NodeAccess,
  value: String,
  description: String,
  icon: IconLookup,
  inherited: Option[(Graph, NodeId) => PermissionDescription] = None
)

object Permission {
  val inherit = PermissionDescription(
    access = NodeAccess.Inherited,
    value = "Inherited",
    description = "The permissions for this page are the same as for its parents", // TODO: write name of parent page. Notion did permission UI very well.
    icon = Icons.permissionInherit,
    inherited = Some((graph, nodeId) => resolveInherited(graph, nodeId))
  )

  val public = PermissionDescription(
    access = NodeAccess.Level(AccessLevel.ReadWrite),
    value = "Public",
    description = "Anyone can access this page via the URL",
    icon = Icons.permissionPublic,
  )

  val `private` = PermissionDescription(
        access = NodeAccess.Level(AccessLevel.Restricted),
        value = "Private",
        description = "Only you and explicit members can access this page",
        icon = Icons.permissionPrivate
      )

  val all: List[PermissionDescription] = inherit :: public :: `private` :: Nil

  def resolveInherited(graph: Graph, nodeId: NodeId): PermissionDescription = {
      val level = graph.accessLevelOfNode(nodeId)
      val isPublic = level.fold(false)(_ == AccessLevel.ReadWrite)
      val inheritedLevel = if(isPublic) {
        Permission.public
      } else {
        Permission.`private`
      }
    inheritedLevel
  }

  def canWrite(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): Boolean = NodePermission.canWrite(state, channel.id).now //TODO reactive? but settingsmenu is anyhow rerendered

  def permissionItem(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VDomModifier = channel match {
    case channel: Node.Content if canWrite(state, channel) =>
      div(
        cls := "item",
        Elements.icon(Icons.userPermission)(marginRight := "5px"),
        span(cls := "text", "Set Permissions", cursor.pointer),
        div(
          cls := "menu",
          Permission.all.map { item =>
            div(
              cls := "item",
              Elements.icon(item.icon)(marginRight := "5px"),
              // value := selection.value,
              Rx {
                item.inherited match {//TODO: report Scala.Rx bug, where two reactive variables in one function call give a compile error: selection.name(state.user().id, node.id, state.graph())
                  case None => item.value
                  case Some(inheritance) => s"Inherited (${inheritance(state.graph(), channel.id).value})"
                }
              },
              (channel.meta.accessLevel == item.access).ifTrueOption(i(cls := "check icon", margin := "0 0 0 20px")),
              onClick(GraphChanges.addNode(channel.copy(meta = channel.meta.copy(accessLevel = item.access)))) --> state.eventProcessor.changes,
              onClick foreach {
                Analytics.sendEvent("pageheader", "changepermission", item.access.str)
              }
            )
          }
        )
      )
    case _ => VDomModifier.empty
  }

}
