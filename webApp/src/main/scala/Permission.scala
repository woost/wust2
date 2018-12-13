package wust.webApp

import fontAwesome.IconLookup
import wust.graph.Graph
import wust.ids.{AccessLevel, NodeAccess, NodeId}

case class PermissionDescription(
  access: NodeAccess,
  value: String,
  name: (NodeId, Graph) => String,
  description: String,
  icon: IconLookup
)

object Permission {
  val inherit = PermissionDescription(
    access = NodeAccess.Inherited,
    name = { (nodeId, graph) =>
      val level = graph.accessLevelOfNode(nodeId)
      val isPublic = level.fold(false)(_ == AccessLevel.ReadWrite)
      val inheritedLevel = if(isPublic) "Public" else "Private"
      s"Inherited ($inheritedLevel)"
    },
    value = "Inherited",
    description = "The permissions for this page are the same as for its parents", // TODO: write name of parent page. Notion did permission UI very well.
    icon = Icons.permissionInherit,
  )

  val public = PermissionDescription(
    access = NodeAccess.Level(AccessLevel.ReadWrite),
    name = (_, _) => "Public",
    value = "Public",
    description = "Anyone can access this page via the URL",
    icon = Icons.permissionPublic,
  )

  val `private` = PermissionDescription(
        access = NodeAccess.Level(AccessLevel.Restricted),
        name = (_, _) => "Private",
        value = "Private",
        description = "Only you and explicit members can access this page",
        icon = Icons.permissionPrivate
      )

  val all = inherit :: public :: `private` :: Nil
}
