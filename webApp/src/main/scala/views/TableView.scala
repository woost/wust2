package wust.webApp.views

import fontAwesome.freeSolid
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph.{Graph, GraphChanges, Node}
import wust.ids.{NodeData, NodeId, NodeRole}
import wust.webApp.ItemProperties
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState}

import scala.collection.breakOut

object TableView {
  def apply(state: GlobalState, focusState: FocusState, roles: List[NodeRole])(implicit ctx: Ctx.Owner): VNode = {
    val sort = Var[Option[UI.ColumnSort]](None)

    div(
      Styles.growFull,
      overflow.auto,

      Rx {
        val graph = state.graph()
        table(state, graph, focusState.focusedId, roles, sort)
      }
    )
  }

  def table(state: GlobalState, graph: Graph, focusedId: NodeId, roles: List[NodeRole], sort: Var[Option[UI.ColumnSort]])(implicit ctx: Ctx.Owner): VDomModifier = {
    val focusedIdx = graph.idToIdxOrThrow(focusedId)

    def columnEntryOfNodes(row: NodeId, nodes: Array[_ <: Node], valueModifier: VDomModifier = VDomModifier.empty, rowModifier: VDomModifier = VDomModifier.empty): UI.ColumnEntry = UI.ColumnEntry(
      sortValue = nodes.map {
        case node: Node.Content => node.str
        case user: Node.User    => Components.displayUserName(user.data) // sort users by display name
      }.mkString(", "),
      value = VDomModifier(
        nodes.map {
          case tag: Node.Content if tag.role == NodeRole.Tag => Components.removableNodeTag(state, tag, row)
          case node: Node.Content                            => Components.editableNodeOnClick(state, node, maxLength = Some(50))
          case user: Node.User                               => Components.removableAssignedUser(state, user, row)
        },
        valueModifier
      ),
      rowModifier = rowModifier
    )

    val childrenIdxs: Array[Int] = {
      val arr = graph.notDeletedChildrenIdx(focusedIdx).toArray
      if (roles.isEmpty) arr else arr.filter { childrenIdx =>
        val node = graph.nodes(childrenIdx)
        roles.contains(node.role)
      }
    }

    val propertyGroup = PropertyData.Group(graph, childrenIdxs)

    val nodeColumns: List[UI.Column] =
      UI.Column(
        "Node",
        propertyGroup.infos.map { property =>
          columnEntryOfNodes(property.node.id, Array(property.node), valueModifier = Components.sidebarNodeFocusClickMod(state, property.node.id), rowModifier = Components.sidebarNodeFocusVisualizeMod(state, property.node.id))
        }(breakOut)
      ) ::
      UI.Column(
        "Tags",
        propertyGroup.infos.map { property =>
          columnEntryOfNodes(property.node.id, property.tags)
        }(breakOut)
      ) ::
      UI.Column(
        "Assigned",
        propertyGroup.infos.map { property =>
          columnEntryOfNodes(property.node.id, property.assignedUsers)
        }(breakOut)
      ) ::
      Nil

    val propertyColumns: List[UI.Column] = propertyGroup.properties.map { property =>
      UI.Column(
        property.key,
        property.groups.map { group =>
          columnEntryOfNodes(group.nodeId, group.values.map(_.node))
        }(breakOut)
      )
    }(breakOut)

    VDomModifier(
      div(
        width := "100%",
        Styles.flex,
        alignItems.flexStart,
        UI.sortableTable(nodeColumns ::: propertyColumns, sort),

        ItemProperties.manageProperties(state, nodeId = focusedId, targetNodeIds = Some(propertyGroup.infos.map(_.node.id)), contents = button(
          cls := "ui button",
          freeSolid.faPlus,
          cursor.pointer,
        ))
      ),

      button(
        cls := "ui button",
        freeSolid.faPlus,
        cursor.pointer,
        onClick.stopPropagation.foreach {
          val targetRole = roles match {
            case head :: _ => head
            case Nil       => NodeRole.default
          }

          val newNode = Node.Content(NodeData.Markdown(""), targetRole)

          sort() = None // reset sorting again, so the new node appears at the bottom :)
          state.eventProcessor.changes.onNext(GraphChanges.addNodeWithParent(newNode, focusedId))

          ()
        }
      )
    )
  }

}
