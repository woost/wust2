package wust.webApp.views

import fontAwesome.{freeRegular, freeSolid}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{CommonStyles, Styles}
import wust.graph.{Edge, Graph, GraphChanges, Node}
import wust.ids._
import wust.webApp.ItemProperties
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState}
import wust.webApp.views.SharedViewElements.onClickNewNamePrompt

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

    val targetRole = roles match {
      case head :: _ => head
      case Nil       => NodeRole.default
    }

    def columnEntryOfNodes(row: NodeId, nodes: Array[_ <: Node], cellModifier: VDomModifier = VDomModifier.empty): UI.ColumnEntry = UI.ColumnEntry(
      sortValue = nodes.map {
        case node: Node.Content => node.str
        case user: Node.User    => Components.displayUserName(user.data) // sort users by display name
      }.mkString(", "),
      value = VDomModifier(
        nodes.map {
          case tag: Node.Content if tag.role == NodeRole.Tag => Components.removableNodeTag(state, tag, row)
          case node: Node.Content                            => Components.editableNodeOnClick(state, node, maxLength = Some(50), config = EditableContent.Config.default)
          case user: Node.User                               => Components.removableAssignedUser(state, user, row)
        },
        cellModifier
      )
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
        "",
        propertyGroup.infos.zipWithIndex.map { case (property, idx) =>
          UI.ColumnEntry("",
            VDomModifier(
             backgroundColor := "#f9fafb", // same color as header of table
             Components.sidebarNodeFocusVisualizeRightMod(state.rightSidebarNode, property.node.id),
             Components.sidebarNodeFocusClickMod(state.rightSidebarNode, property.node.id),
             div(
               fontSize.xxSmall,
               idx + 1,
             )
            ),
            rowModifier = Components.sidebarNodeFocusVisualizeMod(state.rightSidebarNode, property.node.id)
          )
        }(breakOut),
        sortable = false
      ) ::
      UI.Column(
        "Node",
        propertyGroup.infos.map { property =>
          columnEntryOfNodes(property.node.id, Array(property.node))
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
      val predictedType = property.groups.headOption.flatMap(g => g.values.headOption.map(_.node).map(_.data.tpe))
      UI.Column(
        property.key,
        property.groups.map { group =>
          columnEntryOfNodes(group.nodeId, group.values.map(_.node),
            cellModifier = VDomModifier.ifTrue(group.values.isEmpty)(
              cls := "orange",
              display.tableCell, // needed because semantic ui rewrites the td cell to be inline-block, but that messes with our layout.
              div(
                Styles.growFull,
                Styles.flex,
                alignItems.center,
                div(freeSolid.faPlus, cls := "fa-fw", marginLeft.auto, marginRight.auto),
              ),
              ItemProperties.managePropertiesDropdown(state, nodeId = group.nodeId, prefilledType = predictedType, prefilledKey = property.key),
            )
          )
        }(breakOut)
      )
    }(breakOut)

    val keepPropertyAsDefault = Var(false)

    VDomModifier(
      div(
        width := "100%",
        Styles.flex,
        alignItems.flexStart,
        UI.sortableTable(nodeColumns ::: propertyColumns, sort),

        div(
          button(
            cls := "ui mini compact button",
            freeSolid.faPlus
          ),
          ItemProperties.managePropertiesDropdown(state, nodeId = focusedId, targetNodeIds = Some(propertyGroup.infos.map(_.node.id)),
            dropdownModifier = cls := "top right",
            descriptionModifier = div(
              padding := "10px",
              div(
                UI.toggle("Keep as default", keepPropertyAsDefault).apply(marginBottom := "5px"),
                GraphChangesAutomationUI.settingsButton(state, focusedId, activeColor = CommonStyles.selectedNodesBgColorCSS).prepend(
                  span("Manage automations", textDecoration.underline, marginRight := "5px")
                )
              )
            ),
            extendNewProperty = { (edgeData, propertyNode) =>
              if (keepPropertyAsDefault.now) {
                val newPropertyNode = propertyNode.copy(id = NodeId.fresh)
                val templateNode = Node.Content(NodeData.Markdown(s"Default for row '${edgeData.key}'"), targetRole)
                GraphChanges(
                  addNodes = Set(templateNode, newPropertyNode),
                  addEdges = Set(
                    Edge.LabeledProperty(templateNode.id, edgeData, propertyId = PropertyId(newPropertyNode.id)),
                    Edge.Automated(focusedId, templateNodeId = TemplateId(templateNode.id)),
                    Edge.Child(childId = ChildId(templateNode.id), parentId = ParentId(focusedId))
                  )
                )
              } else GraphChanges.empty
            }
          ),
        )
      ),

      button(
        cls := "ui mini compact button",
        freeSolid.faPlus,
        cursor.pointer,
        UI.popup("center right") := "Add a new Row",
        onClickNewNamePrompt(state, header = "Add a new Row", placeholderMessage = Some(s"A new ${targetRole}")).foreach { str =>
          val newNode = Node.Content(NodeData.Markdown(str), targetRole)

          sort() = None // reset sorting again, so the new node appears at the bottom :)
          state.eventProcessor.changes.onNext(GraphChanges.addNodeWithParent(newNode, focusedId))

          ()
        }
      )
    )
  }

}
