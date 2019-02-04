package wust.webApp

import fontAwesome._
import googleAnalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views.{Components, Elements, UI}

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

  val all: List[PermissionDescription] = `private` :: public :: inherit :: Nil

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

  def permissionItem(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VDomModifier = channel match {
    case channel: Node.Content =>
      div(
        cls := "item",
        Elements.icon(Icons.userPermission),
        span("Set Permissions"),
        Components.horizontalMenu(
          Permission.all.map { item =>
            Components.MenuItem(
              title = Elements.icon(item.icon),
              description = Rx {
                item.inherited match {//TODO: report Scala.Rx bug, where two reactive variables in one function call give a compile error: selection.name(state.user().id, node.id, state.graph())
                  case None => item.value
                  case Some(inheritance) => s"Inherited (${inheritance(state.graph(), channel.id).value})"
                }
              },
              active = channel.meta.accessLevel == item.access,
              clickAction = { () =>
                state.eventProcessor.changes.onNext(GraphChanges.addNode(channel.copy(meta = channel.meta.copy(accessLevel = item.access))))
                Analytics.sendEvent("pageheader", "changepermission", item.access.str)
              }
            )
          }
        )
      )
    case _ => VDomModifier.empty
  }

}
