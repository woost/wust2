package wust.webApp

import fontAwesome._
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.facades.googleanalytics.Analytics
import wust.graph._
import wust.ids._
import wust.webApp.state._
import wust.webApp.views.Components
import wust.webApp.views.Components._
import wust.webUtil.{Elements, UI}
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

  val public = PermissionDescription(
    access = NodeAccess.Level(AccessLevel.ReadWrite),
    value = "Public",
    description = "Anyone can access this page via URL",
    icon = Icons.permissionPublic,
  )

  val publicRead = PermissionDescription(
    access = NodeAccess.Level(AccessLevel.Read),
    value = "Public-Read",
    description = "Anyone can access this page readonly via URL",
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
    val isPublic = level.fold(false)(n => n == AccessLevel.ReadWrite || n == AccessLevel.Read)

    if(isPublic) Permission.public else Permission.`private`
  }

  def permissionIndicator(level: PermissionDescription, modifier: VDomModifier = VDomModifier.empty): BasicVNode = {
    div(level.icon, Styles.flexStatic, UI.popup("bottom center") := level.description, modifier)
  }

  def permissionIndicatorIfPublic(level: PermissionDescription, modifier: VDomModifier = VDomModifier.empty): VDomModifier = {
    VDomModifier.ifTrue(level.access == NodeAccess.ReadWrite || level.access == NodeAccess.Read)(div(level.icon, Styles.flexStatic, UI.popup("bottom center") := level.description, modifier))
  }

  def permissionItem(channel: Node.Content)(implicit ctx: Ctx.Owner): VDomModifier = {
    a(
      cls := "item",
//      Components.icon(Icons.userPermission),
      span("Permissions"),
      Components.horizontalMenu(
        Permission.all.map { item =>
          Components.MenuItem(
            title = Elements.icon(item.icon),
            description = Rx {
              item.inherited match {
                case None => item.value
                case Some(inheritance) => s"Inherited (${inheritance(GlobalState.rawGraph(), channel.id).value})"
              }
            },
            active = channel.meta.accessLevel == item.access,
            clickAction = { () =>
              GlobalState.submitChanges(GraphChanges.addNode(channel.copy(meta = channel.meta.copy(accessLevel = item.access))))
              FeatureState.use(Feature.ChangeAccessLevel)
            }
          )
        }
      )
    )
  }

}
