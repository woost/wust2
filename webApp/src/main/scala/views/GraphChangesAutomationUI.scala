package wust.webApp.views

import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp.outwatchHelpers._
import rx.{Ctx, Rx, Var}
import wust.css.Styles
import wust.graph.{Edge, GraphChanges, Node, Page}
import wust.ids.{ChildId, NodeId, ParentId, TemplateId}
import wust.webApp.dragdrop.DragItem
import wust.webApp.{Icons, Ownable}
import wust.webApp.state.GlobalState

// Offers methods for rendering components for the GraphChangesAutomation.

object GraphChangesAutomationUI {

  // returns the modal config for rendering a modal for configuring automation of the node `nodeId`.
  def modalConfig(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): UI.ModalConfig = {
    val header: VDomModifier = Rx {
      state.rawGraph().nodesByIdGet(nodeId).map { node =>
        UI.ModalConfig.defaultHeader(state, node, "Automation", Icons.automate)
      }
    }

    val description: VDomModifier = div(
      state.graph.map { graph =>
        val templates = graph.templateNodes(graph.idToIdx(nodeId))
        if(templates.isEmpty) {
          b("This node is currently not automated")
        } else {
          VDomModifier(
            b("This node has existing automation templates that will be applied to every child of this node:"),
            div(
              padding := "10px",
              state.rawGraph.map(_.nodesByIdGet(state.page.now.parentId.get).map(PageHeader.channelMembers(state, _))),
              div(
                height := "400px",
                overflowY.auto,
                Styles.flex,
                justifyContent.spaceBetween,

                Components.registerDragContainer(state),

                Components.removeableList[Node](
                  templates,
                  state.eventProcessor.changes.redirectMap(templateNode => GraphChanges(delEdges = Set(Edge.Automated(nodeId, TemplateId(templateNode.id))))),
                  tooltip = Some("Remove this template")
                ) { templateNode =>
                  KanbanView.renderCard(
                    state,
                    templateNode,
                    parentId = null, //TODO :)
                    pageParentId = null, //TODO :)
                    path = Nil,
                    selectedNodeIds = Var(Set.empty),
                    activeAddCardFields = Var(Set.empty),
                    showCheckbox = false,
                    isDone = false,
                    dragPayload = _ => DragItem.DisableDrag,
                    dragTarget = DragItem.Task.apply
                  ).apply(
                    minWidth := "250px",
                    state.rawGraph.map(g => VDomModifier.ifNot(g.parents(templateNode.id).contains(nodeId))("* Template is not a direct child of the current node." ))
                  )
                },

                SharedViewElements.tagListBar(state, state.page.now.parentId.get, expandable = false),
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
        "Create automation template",
        cls := "ui button primary",
        cursor.pointer,

        onClick.mapTo {
          val templateNode = Node.MarkdownTask("Template")
          GraphChanges(addEdges = Set(Edge.Child(ParentId(nodeId), ChildId(templateNode.id)), Edge.Automated(nodeId, TemplateId(templateNode.id))), addNodes = Set(templateNode))
        } --> state.eventProcessor.changes,
      ),

      Components.searchInGraph(state.graph, placeholder = "Add existing template", filter = {
        case content: Node.Content => true
        case _ => false
      }).foreach { selectedTemplateNodeId =>
        state.eventProcessor.changes onNext GraphChanges(addEdges = Set(Edge.Automated(nodeId, TemplateId(selectedTemplateNodeId))))
      },
    )

    UI.ModalConfig(header = header, description = description, actions = Some(actions))
  }

  // a settings button for automation that opens the modal on click.
  def settingsButton(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Elements.icon(Icons.automate)(
        marginRight := "5px",

        Rx {
          val graph = state.rawGraph()
          val templates = graph.templateNodes(graph.idToIdx(nodeId))

          if (templates.isEmpty) VDomModifier(color := "grey", UI.popup := "Automation: inactive")
          else VDomModifier(color := "white", UI.popup := "Automation: active")
        }
      ),

      cursor.pointer,
      onClick(Ownable(implicit ctx => modalConfig(state, nodeId))) --> state.uiModalConfig
    )
  }
}
