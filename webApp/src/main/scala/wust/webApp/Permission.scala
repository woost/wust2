package wust.webApp

import fontAwesome._
import outwatch._
import outwatch.dsl._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webUtil.UI
import wust.webUtil.outwatchHelpers._

final case class PermissionDescription(
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

  val viaLink = PermissionDescription(
    access = NodeAccess.Level(AccessLevel.ReadWrite),
    value = "Public",
    description = "Accessible via Link",
    icon = Icons.permissionLink,
  )

  val viaLinkReadonly = PermissionDescription(
    access = NodeAccess.Level(AccessLevel.Read),
    value = "Public",
    description = "Viewable via Link",
    icon = Icons.permissionLinkReadonly,
  )

  val `private` = PermissionDescription(
    access = NodeAccess.Level(AccessLevel.Restricted),
    value = "Private",
    description = "Private (Only selected members can access)",
    icon = Icons.permissionPrivate
  )

  val all: List[PermissionDescription] = `private` :: viaLink :: viaLinkReadonly :: inherit :: Nil

  private def permissionDescriptionWithoutInherit(accessLevel: NodeAccess): Option[PermissionDescription] = {
    accessLevel match {
      case NodeAccess.Level(level) if level == AccessLevel.ReadWrite => Some(Permission.viaLink)
      case NodeAccess.Level(level) if level == AccessLevel.Read => Some(Permission.viaLinkReadonly)
      case NodeAccess.Level(level) if level == AccessLevel.Restricted => Some(Permission.`private`)
      case _ => None
    }
  }

  def permissionIndicatorWithoutInherit(node: Node): Option[VNode] = {
    Permission.permissionDescriptionWithoutInherit(node.meta.accessLevel).map { descr =>
      Permission.permissionIndicator(descr).apply(
        opacity := 0.3,
      )
    }
  }

  def resolveInherited(graph: Graph, nodeId: NodeId): PermissionDescription = {
    val level = graph.accessLevelOfNode(nodeId)
    val isPublic = level.fold(false)(n => n == AccessLevel.ReadWrite || n == AccessLevel.Read)

    if (isPublic) Permission.viaLink else Permission.`private`
  }

  def permissionIndicator(level: PermissionDescription, modifier: VDomModifier = VDomModifier.empty): BasicVNode = {
    div(level.icon, Styles.flexStatic, UI.tooltip := level.description, modifier)
  }

}
