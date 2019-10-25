package wust.webApp.views

import fontAwesome._
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.ext.monix._
import rx._
import wust.css.Styles
import wust.graph.{Edge, Graph, GraphChanges, Node}
import wust.ids._
import wust.util.StringOps
import wust.webApp.dragdrop.{DragContainer, DragItem}
import wust.webApp.state.{FocusState, GlobalState, GraphChangesAutomation, Placeholder}
import wust.webApp.views.DragComponents.registerDragContainer
import wust.webApp.views.SharedViewElements.onClickNewNamePrompt
import wust.webApp.{Icons, ItemProperties}
import wust.webUtil.UI
import wust.webUtil.outwatchHelpers._

import scala.collection.{breakOut, mutable}

object TableView {
  def apply(focusState: FocusState, roles: List[NodeRole], viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): VNode = {
    val sort = Var[Option[UI.ColumnSort]](None)

    div(
      keyed,
      Styles.growFull,
      overflow.auto,
      paddingTop := "20px",

      Rx {
        val graph = GlobalState.graph()
        table( graph, focusState.focusedId, roles, sort, viewRender)
      }
    )
  }

  def table(graph: Graph, focusedId: NodeId, roles: List[NodeRole], sort: Var[Option[UI.ColumnSort]], viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): VDomModifier = {
    val focusedIdx = graph.idToIdxOrThrow(focusedId)

    val globalEditMode = Var(Option.empty[(String, Seq[Edge.LabeledProperty])])

    val targetRole = roles match {
      case head :: _ => head
      case Nil       => NodeRole.default
    }

    def columnEntryOfNodes(row: NodeId, edges: Seq[(Option[Edge.LabeledProperty], Node)], cellModifier: VDomModifier = VDomModifier.empty): UI.ColumnEntry = UI.ColumnEntry(
      sortValue = edges.map {
        case (_, node: Node.Content) => node.str
        case (_, user: Node.User) => Components.displayUserName(user.data) // sort users by display name
      }.mkString(", "),
      value = VDomModifier(
        edges.map {
          case (Some(edge), node: Node.Content) => Components.editablePropertyNodeOnClick( node, edge, maxLength = Some(50), config = EditableContent.Config.default)
          case (_, tag: Node.Content) if tag.role == NodeRole.Tag => Components.removableNodeTag( tag, row)
          case (_, stage: Node.Content) if stage.role == NodeRole.Stage => Components.removableNodeTag( stage, row)
          case (_, node: Node.Content) => Components.editableNodeOnClick( node, maxLength = Some(50), config = EditableContent.Config.default)
          case (_, user: Node.User)                               => Components.removableAssignedUser( user, row)
        },
        position.relative, // for cancel and save button absolute popup
        cellModifier
      )
    )

    def columnHeader(name: String) = VDomModifier(
      name,
      minWidth := "100px"
    )

    def columnHeaderWithDelete(name: String, edges: Seq[Edge.LabeledProperty]) = {
      val editMode = Var(false)
      var lastEditMode = false
      editMode.triggerLater { editMode =>
        if (editMode) globalEditMode() = Some(name -> edges)
        else if (globalEditMode.now.exists { case (key, _) => key == name }) globalEditMode() = None
      }

      def miniButton = span(
        fontSize.xSmall,
        cursor.pointer,
      )

      span(
        Styles.inlineFlex,
        justifyContent.spaceBetween,

        div(
          position.relative, // for cancel and save button absolute popup
          EditableContent.inlineEditorOrRender[String](name, editMode, _ => columnHeader(_)).editValue.foreach { newName =>
            if (newName.nonEmpty) {
              GlobalState.submitChanges(GraphChanges(delEdges = edges.map(e => e)(breakOut)) merge GraphChanges(addEdges = edges.map(edge => edge.copy(data = edge.data.copy(key = newName)))(breakOut)))
            }
          }
        ),

        editMode.map {
          case false => miniButton(
            paddingLeft := "5px",
            Icons.edit,
            onClick.stopPropagation.use(true) --> editMode
          )
          case true => VDomModifier.empty
        }
      )
    }

    val childrenIdxs: Array[Int] = {
      val arr = graph.childrenIdx(focusedIdx).toArray
      if (roles.isEmpty) arr else arr.filter { childrenIdx =>
        val node = graph.nodes(childrenIdx)
        roles.contains(node.role)
      }
    }

    val propertyGroup = PropertyData.Group(graph, childrenIdxs)

    val nodeColumns: Seq[UI.Column] = {
      val columns = new mutable.ArrayBuffer[UI.Column]

      columns += UI.Column(
        "#",
        propertyGroup.infos.zipWithIndex.map { case (property, idx) =>
          UI.ColumnEntry(idx,
            VDomModifier(
             backgroundColor := "#f9fafb", // same color as header of table
             Components.sidebarNodeFocusVisualizeRightMod(GlobalState.rightSidebarNode, property.node.id),
             Components.sidebarNodeFocusClickMod(GlobalState.rightSidebarNode, property.node.id),
             div(
               fontSize.xxSmall,
               idx + 1,
             )
            ),
            rowModifier = VDomModifier(
              Components.sidebarNodeFocusVisualizeMod(GlobalState.rightSidebarNode, property.node.id),
              DragItem.fromNodeRole(property.node.id, property.node.role).map(item => DragComponents.drag(target = item))
            )
          )
        }(breakOut)
      )

      columns += UI.Column(
        columnHeader("Name"),
        propertyGroup.infos.map { property =>
          columnEntryOfNodes(property.node.id, Array(None -> property.node))
        }(breakOut)
      )

      if(propertyGroup.infos.exists(_.tags.nonEmpty))
        columns += UI.Column(
          columnHeader("Tags"),
          propertyGroup.infos.map { property =>
            columnEntryOfNodes(property.node.id, property.tags.map(None -> _))
          }(breakOut)
        )

      if(propertyGroup.infos.exists(_.stages.nonEmpty))
        columns += UI.Column(
          columnHeader("Stage"),
          propertyGroup.infos.map { property =>
            columnEntryOfNodes(property.node.id, property.stages.map(None -> _))
          }(breakOut)
        )

      if(propertyGroup.infos.exists(_.assignedUsers.nonEmpty))
        columns += UI.Column(
          columnHeader("Assigned"),
          propertyGroup.infos.map { property =>
            columnEntryOfNodes(property.node.id, property.assignedUsers.map(None -> _))
          }(breakOut)
        )

      columns
    }

    val propertyColumns: List[UI.Column] = propertyGroup.properties.map { property =>
      val (predictedType, predictedShowOnCard) = property.groups.find(_.values.nonEmpty).fold((Option.empty[NodeTypeSelection], false)) { group =>
        val value = group.values.head
        val node = value.node
        val edge = value.edge
        val tpe = node.role match {
          case NodeRole.Neutral => NodeTypeSelection.Data(node.data.tpe)
          case _ => NodeTypeSelection.Ref
        }
        (Some(tpe), edge.data.showOnCard)
      }
      UI.Column(
        columnHeaderWithDelete(property.key, property.groups.flatMap(_.values.map(_.edge))),
        property.groups.map { group =>
          columnEntryOfNodes(group.node.id, group.values.map(v => Some(v.edge) -> v.node),
            cellModifier = VDomModifier.ifTrue(group.values.isEmpty)(
              cls := "grey",
              display.tableCell, // needed because semantic ui rewrites the td cell to be inline-block, but that messes with our layout.
              div(
                Styles.growFull,
                Styles.flex,
                alignItems.center,
                div(freeSolid.faPlus, cls := "fa-fw", marginLeft.auto, marginRight.auto),
              ),
              ItemProperties.managePropertiesDropdown(
                ItemProperties.Target.Node(group.node.id),
                ItemProperties.TypeConfig(prefilledType = predictedType, hidePrefilledType = true),
                ItemProperties.EdgeFactory.labeledProperty(property.key, predictedShowOnCard)
              ),
            )
          )
        }(breakOut)
      )
    }(breakOut)

    val keepPropertyAsDefault = Var(false)

    VDomModifier(
      div(
        cls := "ui mini form",
        width := "100%",
        padding := "5px",
        Styles.flex,
        alignItems.flexStart,
        UI.sortableTable(nodeColumns ++ propertyColumns, sort),

        VDomModifier.ifTrue(propertyGroup.infos.nonEmpty)(div(
          Styles.flexStatic,
          Styles.flex,
          flexDirection.column,
          margin := "10px",

          div(
            button(
              cls := "ui mini compact button",
              "+ New Column"
            ),
            ItemProperties.managePropertiesDropdown(
              target = ItemProperties.Target.Custom({ (selectedKey, changesf) =>
                if (keepPropertyAsDefault.now) {
                  val templateNode = Node.Content(NodeData.Markdown(s"Default for row '${selectedKey.fold("")(_.string)}'"), targetRole)
                  val changes = changesf(templateNode.id) merge GraphChanges(
                    addNodes = Array(templateNode),
                    addEdges = Array(
                      Edge.Child(ParentId(focusedId), ChildId(templateNode.id)),
                      Edge.Automated(focusedId, templateNodeId = TemplateId(templateNode.id))
                    )
                  )
                  // now we add these changes with the template node to a temporary graph, because ChangesAutomation needs the template node in the graph
                  val tmpGraph = GlobalState.rawGraph.now applyChanges changes
                  val templateIdx = tmpGraph.idToIdxOrThrow(templateNode.id)
                  // run automation of this template for each row
                  propertyGroup.infos.foldLeft[GraphChanges](changes)((changes, info) => changes merge GraphChangesAutomation.copySubGraphOfNode(GlobalState.user.now.id, tmpGraph, info.node, templateNodeIdxs = Array(templateIdx)))
                } else propertyGroup.infos.foldLeft[GraphChanges](GraphChanges.empty)((changes, info) => changes merge changesf(info.node.id))
              }, keepPropertyAsDefault),
              dropdownModifier = cls := "top left",
              descriptionModifier = div(
                padding := "10px",
                div(
                  UI.toggle("Keep as default", keepPropertyAsDefault).apply(marginBottom := "5px"),

                  // GraphChangesAutomationUI.settingsButton(
                  //   focusedId,
                  //   activeMod = visibility.visible,
                  //   viewRender = viewRender,
                  //   tooltipDirection = "left center"
                  // ).prepend(span("Manage automations", marginRight := "5px"))
                )
              ),
            ),
          ),

          globalEditMode.map[VDomModifier] {
            case Some((name, edges)) => div(
              Styles.flex,
              alignItems.flexEnd,
              flexDirection.column,
              marginTop := "10px",
              padding := "5px",
              backgroundColor := "white",
              boxShadow := "0px 0px 3px 0px rgba(0, 0, 0, 0.75)",
              borderRadius := "3px",

              b(s"Edit Column '$name'", marginBottom := "5px"),

              UI.checkboxEmitter(span(Icons.showOnCard, " Show on Card"), edges.forall(_.data.showOnCard)).map { showOnCard =>
                GraphChanges(addEdges = edges.collect { case edge if edge.data.showOnCard != showOnCard => edge.copy(data = edge.data.copy(showOnCard = showOnCard)) }(breakOut))
              } --> GlobalState.eventProcessor.changes,

              div(
                marginTop := "5px",
                cls := "ui mini compact red button",
                keyed, // TODO: this key is a hack. if we leave it out the onclick event of edit-icon only works once! with this key, it works. outwatch-bug!
                Icons.delete,
                " Delete",
                onClick.stopPropagation.foreach {
                  if(dom.window.confirm(s"Do you really want to remove the column '$name' in all children?")) {
                    GlobalState.submitChanges(GraphChanges(delEdges = edges.map(e => e)(breakOut)))
                  }
                  ()
                },
              )
            )
            case None => VDomModifier.empty
          },

          exportToCsvButton(graph.nodes(focusedIdx), propertyGroup).apply(marginTop := "20px"),
        )),
      ),

      button(
        margin := "10px",
        cls := "ui mini compact button",
        "+ New Row",
        cursor.pointer,
        onClickNewNamePrompt( header = "Add a new Row", placeholder = Placeholder(s"Name of row")).foreach { sub =>
          val newNode = Node.Content(NodeData.Markdown(sub.text), targetRole)

          sort() = None // reset sorting again, so the new node appears at the bottom :)
          val addNode = GraphChanges.addNodeWithParent(newNode, ParentId(focusedId))
          val addTags = ViewFilter.addCurrentlyFilteredTags( newNode.id)
          GlobalState.submitChanges(addNode merge addTags merge sub.changes(newNode.id))

          ()
        }
      ),
      registerDragContainer( DragContainer.Default)
    )
  }

  def exportToCsvButton(node: Node, propertyGroup: PropertyData.Group) = {

    button(
      renderFontAwesomeIcon(Icons.csv).apply(marginRight := "4px"),
      "Export to CSV",
      cls := "ui mini compact button basic",
      cursor.pointer,
      onClick.stopPropagation.foreach {
        val data = CsvHelper.tableToCsv(node, propertyGroup)
        DownloadHelper.promptDownload(
          fileName = StringOps.trimToMaxLength(node.str, 50) + ".csv",
          data = data,
          `type` = Some("text/csv"),
          endings = Some("native")
        )
      }
    )
  }

}
