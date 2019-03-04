package wust.webApp.views

import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp.outwatchHelpers._
import rx.{Ctx, Rx, Var}
import wust.css.{CommonStyles, Styles}
import wust.graph.{Edge, GraphChanges, Node, Page}
import wust.ids.{ChildId, NodeId, ParentId, TemplateId}
import wust.webApp.dragdrop.DragItem
import wust.webApp.{Icons, Ownable}
import wust.webApp.state.{GlobalState, FocusPreference}

import scala.collection.breakOut

// Offers methods for rendering components for the GraphChangesAutomation.

object GraphChangesAutomationUI {

  // returns the modal config for rendering a modal for configuring automation of the node `nodeId`.
  def modalConfig(state: GlobalState, focusedId: NodeId)(implicit ctx: Ctx.Owner): UI.ModalConfig = {
    val header: VDomModifier = Rx {
      state.rawGraph().nodesByIdGet(focusedId).map { node =>
        UI.ModalConfig.defaultHeader(state, node, "Automation", Icons.automate)
      }
    }

    val selectedTemplate = Var[Option[FocusPreference]](None)

    val newTemplateButton = button(
      cls := "ui button",
      cursor.pointer,

      onClick.mapTo {
        val templateNode = Node.MarkdownTask("Template")
        GraphChanges(addEdges = Set(Edge.Child(ParentId(focusedId), ChildId(templateNode.id)), Edge.Automated(focusedId, TemplateId(templateNode.id))), addNodes = Set(templateNode))
      } --> state.eventProcessor.changes,
    )

    val description: VDomModifier = div(

      Rx {
        val graph = state.rawGraph()
        val templates = graph.templateNodes(graph.idToIdx(focusedId))
        if(templates.isEmpty) {
          VDomModifier(
            padding := "10px",
            Styles.flex,
            flexDirection.column,
            b("This node is currently not automated.", alignSelf.flexStart),
            newTemplateButton.apply(
              marginTop := "10px",
              alignSelf.center,
              "Create new Automation Template",
              cls := "primary"
            ),
          )
        } else {
          VDomModifier(
            height := "600px",
            overflowY.auto,
            overflowX.hidden, // hides overflown width with expanded sidebar
            Styles.flex,
            justifyContent.spaceBetween,

            div(
              padding := "10px",
              div(
                b("Active automation templates:"),
                div(fontSize.xSmall, "Each will be applied to every child of this node."),
                marginBottom := "10px",
              ),

              div(
                padding := "10px",
                Styles.flex,
                alignItems.center,
                b(fontSize.small, "Drag Users to assign them:", color.gray, marginRight := "5px"),
                state.rawGraph.map(_.nodesByIdGet(state.page.now.parentId.get).map(PageHeader.channelMembers(state, _))),
              ),

              Components.registerDragContainer(state),

              Components.removeableList[Node](
                templates,
                state.eventProcessor.changes.redirectMap(templateNode => GraphChanges(delEdges = Set(Edge.Automated(focusedId, TemplateId(templateNode.id))))),
              )({ templateNode =>
                  val propertySingle = PropertyData.Single(graph, graph.idToIdxOrThrow(templateNode.id))

                  Components.nodeCard(templateNode, maxLength = Some(100)).apply(
                    padding := "3px",
                    width := "200px",
                    div(
                      Styles.flex,
                      flexWrap.wrap,

                      propertySingle.info.tags.map { tag =>
                        Components.removableNodeTag(state, tag, taggedNodeId = templateNode.id)
                      },

                      propertySingle.properties.map { property =>
                        property.values.map { value =>
                          Components.removablePropertyTag(state, value.edge, value.node)
                        }
                      },

                      {
                        val users: List[VNode] = propertySingle.info.assignedUsers.map { user =>
                          Components.removableUserAvatar(state, user, templateNode.id)
                        }(breakOut)

                        users match {
                          case head :: tail => head.apply(marginLeft := "auto") :: tail
                          case Nil => Nil
                        }
                      },

                      state.rawGraph.map(g => VDomModifier.ifNot(g.parents(templateNode.id).contains(focusedId))(i(color.gray, " * Template is not a direct child of the current node." ))),
                    ),

                    DragItem.fromNodeRole(templateNode.id, templateNode.role).map(dragItem => Components.drag(target = dragItem)),
                    Components.sidebarNodeFocusMod(selectedTemplate, templateNode.id),
                  ).prepend(
                    b(color.gray, templateNode.role.toString)
                  )
              }).apply(paddingLeft := "10px"),
            ),

            position.relative, // needed for right sidebar
            RightSidebar(state, selectedTemplate, nodeId => if (nodeId.isEmpty) selectedTemplate() = None, openModifier = VDomModifier(overflow.auto, marginLeft := "20px")) // overwrite sidebar's marginLeft to smaller value
          )
        }
      },
    )

    val actions: VDomModifier = VDomModifier(
      padding := "0px 10px 10px 10px",
      Styles.flex,
      alignItems.center,
      justifyContent.spaceBetween,

      newTemplateButton.apply(
        "+ Add Template",
        cls := "compact mini",
        margin := "0px" // fixes center alignment issue in chrome
      ),

      Components.searchInGraph(state.graph, placeholder = "Add existing template", inputModifiers = VDomModifier(height := "26px"), filter = {
        case content: Node.Content => true
        case _ => false
      }).foreach { selectedTemplateNodeId =>
        state.eventProcessor.changes onNext GraphChanges(addEdges = Set(Edge.Automated(focusedId, TemplateId(selectedTemplateNodeId))))
      },
    )

    UI.ModalConfig(header = header, description = description, actions = Some(actions), contentModifier = VDomModifier(styleAttr := "padding : 0px !important")) // overwrite padding of modal
  }

  // a settings button for automation that opens the modal on click.
  def settingsButton(state: GlobalState, focusedId: NodeId, activeColor: String = "white", inactiveColor: String = "grey")(implicit ctx: Ctx.Owner): VNode = {
    settingsButtonCustom(state, focusedId, activeMod =  VDomModifier(color := activeColor, UI.popup := "Automation: active"), inactiveMod = VDomModifier(color := inactiveColor, UI.popup := "Automation: inactive"))
  }

  // a settings button for automation that opens the modal on click.
  def settingsButtonCustom(state: GlobalState, focusedId: NodeId, activeMod: VDomModifier, inactiveMod: VDomModifier)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Elements.icon(Icons.automate)(marginRight := "5px"),

      Rx {
        val graph = state.rawGraph()
        val templates = graph.templateNodes(graph.idToIdx(focusedId))
        if (templates.isEmpty) inactiveMod else activeMod
      },
      cursor.pointer,
      onClick(Ownable(implicit ctx => modalConfig(state, focusedId))) --> state.uiModalConfig
    )
  }
}
