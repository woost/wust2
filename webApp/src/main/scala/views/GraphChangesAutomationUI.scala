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
import wust.webApp.state.{GlobalState}

// Offers methods for rendering components for the GraphChangesAutomation.

object GraphChangesAutomationUI {

  // returns the modal config for rendering a modal for configuring automation of the node `nodeId`.
  def modalConfig(state: GlobalState, focusedId: NodeId)(implicit ctx: Ctx.Owner): UI.ModalConfig = {
    val header: VDomModifier = Rx {
      state.rawGraph().nodesByIdGet(focusedId).map { node =>
        UI.ModalConfig.defaultHeader(state, node, "Automation", Icons.automate)
      }
    }

    val selectedTemplate = Var[Option[NodeId]](None)

    val description: VDomModifier = div(
      state.graph.map { graph =>
        val templates = graph.templateNodes(graph.idToIdx(focusedId))
        if(templates.isEmpty) {
          b("This node is currently not automated")
        } else {
          VDomModifier(
            b("This node has existing automation templates that will be applied to every child of this node:"),
            div(
              padding := "10px",
              state.rawGraph.map(_.nodesByIdGet(state.page.now.parentId.get).map(PageHeader.channelMembers(state, _))),
              div(
                height := "600px",
                Styles.flex,
                justifyContent.spaceBetween,

                Components.registerDragContainer(state),

                div(
                  overflowY.auto,
                  Components.removeableList[Node](
                    templates,
                    state.eventProcessor.changes.redirectMap(templateNode => GraphChanges(delEdges = Set(Edge.Automated(focusedId, TemplateId(templateNode.id))))),
                  ) { templateNode =>
                    Components.nodeCard(templateNode, maxLength = Some(100)).apply(
                      minWidth := "200px",
                      Components.sidebarNodeFocusMod(selectedTemplate, templateNode.id),

                      state.rawGraph.map(g => VDomModifier.ifNot(g.parents(templateNode.id).contains(focusedId))(i(color.gray, " * Template is not a direct child of the current node." ))),
                    ).prepend(
                      b(color.gray, templateNode.role.toString)
                    )
                  },
                ),

                div(
                  selectedTemplate.map(t => VDomModifier.ifTrue(t.isEmpty)(display.none)),
                  width := "400px",
                  height := "100%",
                  backgroundColor := CommonStyles.sidebarBgColor,
                  color.white,
                  RightSidebar.content(state, selectedTemplate, nodeId => if (nodeId.isEmpty) selectedTemplate() = None)
                )
              ),
            )
          )
        }
      }
    )

    val actions: VDomModifier = VDomModifier(
      Styles.flex,
      alignItems.center,
      justifyContent.spaceBetween,

      button(
        "Create new automation template",
        cls := "ui button primary",
        cursor.pointer,

        onClick.mapTo {
          val templateNode = Node.MarkdownTask(s"Template for '${state.graph.now.nodesById(focusedId).str}'")
          GraphChanges(addEdges = Set(Edge.Child(ParentId(focusedId), ChildId(templateNode.id)), Edge.Automated(focusedId, TemplateId(templateNode.id))), addNodes = Set(templateNode))
        } --> state.eventProcessor.changes,
      ),

      Components.searchInGraph(state.graph, placeholder = "Add existing template", filter = {
        case content: Node.Content => true
        case _ => false
      }).foreach { selectedTemplateNodeId =>
        state.eventProcessor.changes onNext GraphChanges(addEdges = Set(Edge.Automated(focusedId, TemplateId(selectedTemplateNodeId))))
      },
    )

    UI.ModalConfig(header = header, description = description, actions = Some(actions))
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
