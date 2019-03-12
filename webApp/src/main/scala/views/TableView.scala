package wust.webApp.views

import org.scalajs.dom
import fontAwesome.{freeRegular, freeSolid}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{CommonStyles, Styles}
import wust.graph.{Edge, Graph, GraphChanges, Node}
import wust.ids._
import wust.webApp.{ItemProperties, Icons}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState, GraphChangesAutomation}
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

    def columnEntryOfNodes(row: NodeId, edges: Array[(Option[Edge.LabeledProperty], Node)], cellModifier: VDomModifier = VDomModifier.empty): UI.ColumnEntry = UI.ColumnEntry(
      sortValue = edges.map {
        case (_, node: Node.Content) => node.str
        case (_, user: Node.User) => Components.displayUserName(user.data) // sort users by display name
      }.mkString(", "),
      value = VDomModifier(
        edges.map {
          case (Some(edge), node: Node.Content) => Components.editablePropertyNodeOnClick(state, node, edge, maxLength = Some(50), config = EditableContent.Config.default)
          case (_, tag: Node.Content) if tag.role == NodeRole.Tag => Components.removableNodeTag(state, tag, row)
          case (_, node: Node.Content) => Components.editableNodeOnClick(state, node, maxLength = Some(50), config = EditableContent.Config.default)
          case (_, user: Node.User)                               => Components.removableAssignedUser(state, user, row)
        },
        cellModifier
      )
    )

    def columnHeader(name: String) = VDomModifier(
      name,
    )

    def columnHeaderWithDelete(name: String, edges: Set[Edge.LabeledProperty]) = {
      val editMode = Var(false)
      def miniButton = span(
        marginLeft := "8px",
        fontSize.xSmall,
        cursor.pointer,
      )

      span(
        EditableContent.inputInlineOrRender[String](name, editMode, columnHeader(_)).editValue.foreach { newName =>
          if (newName.nonEmpty) {
            state.eventProcessor.changes.onNext(GraphChanges(delEdges = edges.map(e => e)) merge GraphChanges(addEdges = edges.map(edge => edge.copy(data = edge.data.copy(key = newName)))))
          }
        },
        editMode.map[VDomModifier] {
          case true => miniButton(
            keyed, // TODO: this key is a hack. if we leave it out the onclick event of edit-icon only works once! with this key, it works. outwatch-bug!
            Icons.delete,
            onClick.stopPropagation.foreach {
              if(dom.window.confirm(s"Do you really want to remove the column '$name' in all children?")) {
                state.eventProcessor.changes.onNext(GraphChanges(delEdges = edges.map(e => e)))
              }
              ()
            },
          )
          case false => miniButton(
            Icons.edit,
            onClick.stopPropagation(true) --> editMode
          )
        }
      )
    }

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
        columnHeader(""),
        propertyGroup.infos.zipWithIndex.map { case (property, idx) =>
          UI.ColumnEntry(idx,
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
        }(breakOut)
      ) ::
      UI.Column(
        columnHeader("Node"),
        propertyGroup.infos.map { property =>
          columnEntryOfNodes(property.node.id, Array(None -> property.node))
        }(breakOut)
      ) ::
      UI.Column(
        columnHeader("Tags"),
        propertyGroup.infos.map { property =>
          columnEntryOfNodes(property.node.id, property.tags.map(None -> _))
        }(breakOut)
      ) ::
      UI.Column(
        columnHeader("Assigned"),
        propertyGroup.infos.map { property =>
          columnEntryOfNodes(property.node.id, property.assignedUsers.map(None -> _))
        }(breakOut)
      ) ::
      Nil

    val propertyColumns: List[UI.Column] = propertyGroup.properties.map { property =>
      val predictedType = property.groups.find(_.values.nonEmpty).map(_.values.head.node.data.tpe)
      UI.Column(
        columnHeaderWithDelete(property.key, property.groups.flatMap(_.values.map(_.edge))(breakOut)),
        property.groups.map { group =>
          columnEntryOfNodes(group.nodeId, group.values.map(v => Some(v.edge) -> v.node),
            cellModifier = VDomModifier.ifTrue(group.values.isEmpty)(
              cls := "orange",
              display.tableCell, // needed because semantic ui rewrites the td cell to be inline-block, but that messes with our layout.
              div(
                Styles.growFull,
                Styles.flex,
                alignItems.center,
                div(freeSolid.faPlus, cls := "fa-fw", marginLeft.auto, marginRight.auto),
              ),
              ItemProperties.managePropertiesDropdown(state, ItemProperties.Target.Node(group.nodeId), ItemProperties.Config(prefilledType = predictedType, prefilledKey = property.key)),
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

        VDomModifier.ifTrue(propertyGroup.infos.nonEmpty)(
          div(
            button(
              cls := "ui mini compact button",
              freeSolid.faPlus
            ),
            ItemProperties.managePropertiesDropdown(state,
              target = ItemProperties.Target.Custom({ (edgeData, changesf) =>
                if (keepPropertyAsDefault.now) {
                  val templateNode = Node.Content(NodeData.Markdown(s"Default for row '${edgeData.key}'"), targetRole)
                  val changes = changesf(templateNode.id) merge GraphChanges(
                    addNodes = Set(templateNode),
                    addEdges = Set(
                      Edge.Child(ParentId(focusedId), ChildId(templateNode.id)),
                      Edge.Automated(focusedId, templateNodeId = TemplateId(templateNode.id))
                    )
                  )
                  // now we add these changes with the template node to a temporary graph, because ChangesAutomation needs the template node in the graph
                  val tmpGraph = state.rawGraph.now applyChanges changes
                  // run automation of this template for each row
                  propertyGroup.infos.foldLeft[GraphChanges](changes)((changes, info) => changes merge GraphChangesAutomation.copySubGraphOfNode(state.user.now.id, tmpGraph, info.node, templateNode = templateNode))
                } else propertyGroup.infos.foldLeft[GraphChanges](GraphChanges.empty)((changes, info) => changes merge changesf(info.node.id))
              }, keepPropertyAsDefault),
              dropdownModifier = cls := "top right",
              descriptionModifier = div(
                padding := "10px",
                div(
                  UI.toggle("Keep as default", keepPropertyAsDefault).apply(marginBottom := "5px"),
                  GraphChangesAutomationUI.settingsButton(state, focusedId).prepend(
                    span("Manage automations", textDecoration.underline, marginRight := "5px")
                  ),
                  i(
                    padding := "4px",
                    whiteSpace.normal,
                    s"* The properties you set here will be applied to ${propertyGroup.infos.size} nodes."
                  )
                )
              ),
            ),
          )
        )
      ),

      button(
        cls := "ui mini compact button",
        freeSolid.faPlus,
        cursor.pointer,
        UI.popup("right center") := "Add a new Row",
        onClickNewNamePrompt(state, header = "Add a new Row", placeholderMessage = Some(s"A new ${targetRole}")).foreach { str =>
          val newNode = Node.Content(NodeData.Markdown(str), targetRole)

          sort() = None // reset sorting again, so the new node appears at the bottom :)
          state.eventProcessor.changes.onNext(GraphChanges.addNodeWithParent(newNode, ParentId(focusedId)))

          ()
        }
      )
    )
  }

}
